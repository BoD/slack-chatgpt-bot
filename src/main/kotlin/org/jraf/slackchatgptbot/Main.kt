/*
 * This source is part of the
 *      _____  ___   ____
 *  __ / / _ \/ _ | / __/___  _______ _
 * / // / , _/ __ |/ _/_/ _ \/ __/ _ `/
 * \___/_/|_/_/ |_/_/ (_)___/_/  \_, /
 *                              /___/
 * repository.
 *
 * Copyright (C) 2022-present Benoit 'BoD' Lubek (BoD@JRAF.org)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
@file:Suppress("LoggingStringTemplateAsArgument")

package org.jraf.slackchatgptbot

import kotlinx.coroutines.delay
import org.jraf.slackchatgptbot.Message.BotEmojiReaction
import org.jraf.slackchatgptbot.Message.BotMessage
import org.jraf.slackchatgptbot.Message.UserEmojiReaction
import org.jraf.slackchatgptbot.Message.UserMessage
import org.jraf.slackchatgptbot.arguments.Arguments
import org.jraf.slackchatgptbot.openai.client.OpenAIClient
import org.jraf.slackchatgptbot.slack.client.SlackClient
import org.jraf.slackchatgptbot.slack.client.configuration.ClientConfiguration
import org.jraf.slackchatgptbot.slack.client.configuration.HttpConfiguration
import org.jraf.slackchatgptbot.slack.client.configuration.HttpLoggingLevel
import org.jraf.slackchatgptbot.slack.json.JsonEvent.JsonMessageEvent
import org.jraf.slackchatgptbot.slack.json.JsonEvent.JsonReactionAddedEvent
import org.jraf.slackchatgptbot.slack.json.JsonEvent.JsonUnknownEvent
import org.jraf.slackchatgptbot.slack.json.JsonMember
import org.jraf.slackchatgptbot.util.MaxSizedMutableList
import org.jraf.slackchatgptbot.util.yesterday
import org.slf4j.LoggerFactory
import org.slf4j.simple.SimpleLogger
import java.text.SimpleDateFormat
import java.util.Date
import kotlin.random.Random

private val LOGGER = run {
  // This must be done before any logger is initialized
  System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "trace")
  System.setProperty(SimpleLogger.SHOW_DATE_TIME_KEY, "true")
  System.setProperty(SimpleLogger.DATE_TIME_FORMAT_KEY, "yyyy-MM-dd HH:mm:ss")

  LoggerFactory.getLogger("Main")
}

private const val FAKE_BOT_RESPONSES = false

private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

private val MENTION_ID_REGEX = Regex("<@([A-Z0-9]+)>")
private val MENTION_NAME_REGEX = Regex("@([a-zA-Z0-9_]+)")

private const val PROBLEM_MESSAGE = "Oops there was a problem :( Check the logs."

private const val MESSAGE_TOTAL_HISTORY_SIZE = 40
private const val MESSAGE_COMPLETION_HISTORY_SIZE = 14

private sealed interface Message {
  val threadTs: String?

  data class UserMessage(
    override val threadTs: String?,
    val ts: String,
    val date: Date = Date(),
    val text: String,
    val userName: String,
  ) : Message {
    fun toOpenAiMessage(): String {
      return buildString {
        append(DATE_FORMAT.format(date))
        append(" <$userName> ")
        append(text)
      }
    }
  }

  data class BotMessage(
    override val threadTs: String?,
    val ts: String,
    val text: String,
  ) : Message {
    fun toOpenAiMessage(): String {
      return buildString {
        append(text)
      }
    }
  }

  data class UserEmojiReaction(
    override val threadTs: String?,
    val date: Date = Date(),
    val emoji: String,
    val userName: String,
  ) : Message {
    fun toOpenAiMessage(): String {
      return buildString {
        append(DATE_FORMAT.format(date))
        append(" *** ")
        append(userName)
        append(" reacted with :$emoji:")
      }
    }
  }

  data class BotEmojiReaction(
    override val threadTs: String?,
    val emoji: String,
  ) : Message {
    fun toOpenAiMessage(botName: String): String {
      return buildString {
        append("*** ")
        append(botName)
        append(" reacted with :$emoji:")
      }
    }
  }

}

private val lastMessages = mutableMapOf<String, MutableList<Message>>()

suspend fun main(av: Array<String>) {
  LOGGER.info("Hello, World!")
  val arguments = Arguments(av)
  LOGGER.info("arguments=$arguments")

  val openAIClient = OpenAIClient(
    org.jraf.slackchatgptbot.openai.client.configuration.ClientConfiguration(
      authBearerToken = arguments.openAiAuthToken,
      httpConfiguration = org.jraf.slackchatgptbot.openai.client.configuration.HttpConfiguration(
        loggingLevel = org.jraf.slackchatgptbot.openai.client.configuration.HttpLoggingLevel.ALL,
//        httpProxy = org.jraf.slackchatgptbot.openai.client.configuration.HttpProxy("localhost", 8888)
      )
    )
  )

  val slackClient = SlackClient(
    ClientConfiguration(
      appToken = arguments.slackAppToken,
      botUserOAuthToken = arguments.slackBotUserOAuthToken,
      httpConfiguration = HttpConfiguration(
        loggingLevel = HttpLoggingLevel.ALL,
//        httpProxy = HttpProxy("localhost", 8888)
      )
    )
  )

  LOGGER.info("Getting the list of all members (that could take a while)...")
  val allMembers = slackClient.getAllMembers().associateBy { it.id }
  LOGGER.debug("allMembers=$allMembers")
  val botMember = allMembers.values.first { it.name.equals(arguments.botName, ignoreCase = true) }

  while (true) {
    val webSocketUrl = slackClient.appsConnectionsOpen()
    LOGGER.debug("Connecting to WebSocket webSocketUrl=$webSocketUrl")

    slackClient.openWebSocket(webSocketUrl) { event ->
      LOGGER.debug("event=$event")
      when (event) {
        is JsonUnknownEvent -> {
          LOGGER.warn("Ignoring unknown event type=${event.type}")
          return@openWebSocket
        }

        is JsonReactionAddedEvent -> {
          val messageTs = event.item.ts
          val channel = event.item.channel
          val channelLastMessages = getChannelLastMessages(channel)
          val messageTargetIdx = channelLastMessages.indexOfFirst {
            it is UserMessage && it.ts == messageTs || it is BotMessage && it.ts == messageTs
          }
          if (messageTargetIdx == -1) {
            LOGGER.debug("Ignoring reaction added event because messageTs=$messageTs was not found in channelLastMessages")
            return@openWebSocket
          }
          val messageTarget = channelLastMessages[messageTargetIdx]
          if (messageTarget is BotMessage) {
            LOGGER.debug("Ignoring reaction added event because messageTarget=$messageTarget is a bot message")
            return@openWebSocket
          }
          val threadTs: String? = messageTarget.threadTs
          val isBot = event.user == botMember.id
          val reactionMessage = if (isBot) {
            BotEmojiReaction(
              threadTs = threadTs,
              emoji = event.reaction
            )
          } else {
            UserEmojiReaction(
              emoji = event.reaction,
              userName = allMembers[event.user]!!.name,
              threadTs = threadTs
            )
          }
          channelLastMessages.add(messageTargetIdx + 1, reactionMessage)
          LOGGER.debug("channelLastMessages=\n${channelLastMessages.joinToString("\n")}")

          val randomInt = Random.nextInt(3)
          LOGGER.debug("randomInt=$randomInt")
          if (!isBot && randomInt <= 1) {
            val botResponse =
              getBotResponse(
                botName = arguments.botName,
                openAIClient = openAIClient,
                systemMessage = arguments.reactionsSystemMessage.trim(),
                exampleMessages = arguments.reactionsExampleMessages.mapIndexed { index, text ->
                  val isBotExampleMessage = index % 2 == 1
                  if (isBotExampleMessage) {
                    BotEmojiReaction(
                      emoji = text,
                      threadTs = null,
                    )
                  } else {
                    UserMessage(
                      ts = "",
                      date = yesterday(),
                      userName = text.substringBefore(" ").trim(),
                      text = text.substringAfter(" ").trim(),
                      threadTs = null,
                    )
                  }
                },
                channelLastMessages = channelLastMessages
                  // Only include messages up to the message that was reacted to + the reaction itself
                  .subList(0, messageTargetIdx + 2)
                  .filteredByThread(threadTs)
                  // And only take as much as MESSAGE_COMPLETION_HISTORY_SIZE messages
                  .takeLast(MESSAGE_COMPLETION_HISTORY_SIZE)
              )
            LOGGER.debug("Bot response: $botResponse")
            val emojis = botResponse.split(" ")
              .filter { it.startsWith(":") && it.endsWith(":") }
              .map { it.substring(1, it.length - 1) }
            LOGGER.debug("Emojis: $emojis")
            for (emoji in emojis) {
              try {
                slackClient.reactionsAdd(channel = channel, name = emoji, timestamp = messageTs)
              } catch (e: Exception) {
                LOGGER.warn("Error while adding reaction $emoji", e)
              }
            }
          }
        }

        is JsonMessageEvent -> {
          if (event.text.isBlank() && event.message?.text.isNullOrBlank()) {
            LOGGER.debug("Ignoring message event because text is blank (probably an image)")
            return@openWebSocket
          }
          if (event.text == PROBLEM_MESSAGE) {
            LOGGER.debug("Ignoring problem message")
            return@openWebSocket
          }

          val channelLastMessages = getChannelLastMessages(event.channel)
          if (event.subtype == "message_changed") {
            LOGGER.debug("Message changed event")
            val originalMessageIdx = channelLastMessages.indexOfFirst { it is UserMessage && it.ts == event.previous_message!!.ts }
            if (originalMessageIdx == -1) {
              LOGGER.debug("Ignoring message changed event because previous_message.ts=${event.previous_message!!.ts} was not found in lastMessages")
              return@openWebSocket
            }
            val updatedMessage = (channelLastMessages[originalMessageIdx] as UserMessage).copy(text = event.message!!.text)
            channelLastMessages[originalMessageIdx] = updatedMessage
            LOGGER.debug("channelLastMessages=\n${channelLastMessages.joinToString("\n")}")
            return@openWebSocket
          }

          val isBot = event.user == botMember.id
          channelLastMessages.add(
            if (isBot) {
              BotMessage(
                ts = event.ts,
                threadTs = event.thread_ts,
                text = event.text
              )
            } else {
              UserMessage(
                ts = event.ts,
                threadTs = event.thread_ts,
                userName = allMembers[event.user]!!.name,
                text = event.text.replaceMentionsUserIdToName(allMembers)
              )
            }
          )
          LOGGER.debug("channelLastMessages=\n${channelLastMessages.joinToString("\n")}")

          if (event.text.contains("<@${botMember.id}>") && event.user != botMember.id) {
            val channelLastMessagesForThread = channelLastMessages.filteredByThread(event.thread_ts)
            val botResponse =
              getBotResponse(
                botName = arguments.botName,
                openAIClient = openAIClient,
                systemMessage = arguments.messagesSystemMessage.trim(),
                exampleMessages = arguments.messagesExampleMessages.mapIndexed { index, text ->
                  val isBotExampleMessage = index % 2 == 1
                  if (isBotExampleMessage) {
                    BotMessage(
                      ts = "",
                      threadTs = null,
                      text = text,
                    )
                  } else {
                    UserMessage(
                      ts = "",
                      threadTs = null,
                      date = yesterday(),
                      userName = text.substringBefore(" ").trim(),
                      text = text.substringAfter(" ").trim(),
                    )
                  }
                },
                channelLastMessages = channelLastMessagesForThread.takeLast(MESSAGE_COMPLETION_HISTORY_SIZE)
              )
                .replaceMentionsNameToUserId(allMembers)
            LOGGER.debug("Bot response: $botResponse")
            slackClient.chatPostMessage(channel = event.channel, text = botResponse, threadTs = event.thread_ts)
          }
        }
      }
    }
    LOGGER.debug("Was disconnected, reconnecting...")
    delay(10_000)
  }
}

private fun List<Message>.filteredByThread(threadTs: String?): List<Message> {
  val channelLastMessagesForThread = if (threadTs != null) {
    val threadParentIdx =
      indexOfFirst { it is UserMessage && it.ts == threadTs || it is BotMessage && it.ts == threadTs }
    if (threadParentIdx == -1) {
      LOGGER.debug("Thread $threadTs not found in channelLastMessages")
      emptyList()
    } else {
      val messagesUpToAndIncludingParent = subList(0, threadParentIdx + 1)
      val messagesForThread = filter {
        it is UserMessage && it.threadTs == threadTs ||
          it is BotMessage && it.threadTs == threadTs ||
          it is UserEmojiReaction && it.threadTs == threadTs ||
          it is BotEmojiReaction && it.threadTs == threadTs
      }
      messagesUpToAndIncludingParent + messagesForThread
    }
  } else {
    filter { it.threadTs == null }
  }
  return channelLastMessagesForThread
}

private fun getChannelLastMessages(channel: String) = lastMessages.getOrPut(channel) { MaxSizedMutableList(MESSAGE_TOTAL_HISTORY_SIZE) }

private suspend fun getBotResponse(
  botName: String,
  openAIClient: OpenAIClient,
  systemMessage: String,
  exampleMessages: List<Message>,
  channelLastMessages: List<Message>,
): String {
  val messages = (exampleMessages + channelLastMessages).map {
    when (it) {
      is BotEmojiReaction -> OpenAIClient.Message.Assistant(it.toOpenAiMessage(botName))
      is UserEmojiReaction -> OpenAIClient.Message.User(it.toOpenAiMessage())
      is BotMessage -> OpenAIClient.Message.Assistant(it.toOpenAiMessage())
      is UserMessage -> OpenAIClient.Message.User(it.toOpenAiMessage())
    }
  }
  LOGGER.debug("chatCompletion messages:\n${messages.joinToString("\n")}")
  if (FAKE_BOT_RESPONSES) return "Fake bot response"
  return try {
    openAIClient.chatCompletion(
      model = "gpt-4",
      systemMessage = systemMessage,
      messages = messages,
    )
  } catch (e: Exception) {
    LOGGER.warn("Could not get chat completion with gpt-4, trying gpt-3.5-turbo", e)

    try {
      openAIClient.chatCompletion(
        model = "gpt-3.5-turbo",
        systemMessage = systemMessage,
        messages = messages,
      )
    } catch (e: Exception) {
      LOGGER.warn("Could not get chat completion with gpt-3.5-turbo, give up", e)
      null
    }
  }
    ?: PROBLEM_MESSAGE
}

private fun String.replaceMentionsUserIdToName(allMembers: Map<String, JsonMember>): String {
  return replace(MENTION_ID_REGEX) { matchResult ->
    val memberId = matchResult.groupValues[1]
    val member = allMembers[memberId]
    if (member == null) {
      LOGGER.warn("Could not find member with id $memberId")
      matchResult.value
    } else {
      "@${member.name}"
    }
  }
}

private fun String.replaceMentionsNameToUserId(allMembers: Map<String, JsonMember>): String {
  return replace(MENTION_NAME_REGEX) { matchResult ->
    val memberName = matchResult.groupValues[1]
    val member = allMembers.values.firstOrNull { it.name == memberName }
    if (member == null) {
      LOGGER.warn("Could not find member with name $memberName")
      matchResult.value
    } else {
      "<@${member.id}>"
    }
  }
}
