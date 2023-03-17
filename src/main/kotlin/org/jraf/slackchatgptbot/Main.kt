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
package org.jraf.slackchatgptbot

import kotlinx.coroutines.delay
import org.jraf.slackchatgptbot.arguments.Arguments
import org.jraf.slackchatgptbot.openai.client.OpenAIClient
import org.jraf.slackchatgptbot.slack.client.SlackClient
import org.jraf.slackchatgptbot.slack.client.configuration.ClientConfiguration
import org.jraf.slackchatgptbot.slack.client.configuration.HttpConfiguration
import org.jraf.slackchatgptbot.slack.client.configuration.HttpLoggingLevel
import org.jraf.slackchatgptbot.slack.json.JsonMember
import org.slf4j.LoggerFactory
import org.slf4j.simple.SimpleLogger
import java.text.SimpleDateFormat
import java.util.Date

private val LOGGER = run {
  // This must be done before any logger is initialized
  System.setProperty(SimpleLogger.DEFAULT_LOG_LEVEL_KEY, "trace")
  System.setProperty(SimpleLogger.SHOW_DATE_TIME_KEY, "true")
  System.setProperty(SimpleLogger.DATE_TIME_FORMAT_KEY, "yyyy-MM-dd HH:mm:ss")

  LoggerFactory.getLogger("Main")
}

private val DATE_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
private val MENTION_ID_REGEX = Regex("<@([A-Z0-9]+)>")
private val MENTION_NAME_REGEX = Regex("@([a-zA-Z0-9_]+)")

private data class Message(
  val isAssistant: Boolean,
  val text: String,
)

private val lastMessages = mutableMapOf<String, MutableList<Message>>()

suspend fun main(av: Array<String>) {
  LOGGER.info("Hello, World!")
  val arguments = Arguments(av)

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
      if (event.text.isBlank()) return@openWebSocket

      val eventTextWithMentionsReplaced = event.text.replaceMentionsUserIdToName(allMembers)
      val messageText = buildString {
        append(DATE_FORMAT.format(Date()))
        append(" <")
        append(allMembers[event.user]!!.name)
        append("> ")
        append(eventTextWithMentionsReplaced)
      }
      LOGGER.debug(messageText)
      val isAssistant = event.user == botMember.id
      val channelLastMessages = lastMessages.getOrPut(event.channel) { mutableListOf() }
      channelLastMessages.add(
        if (isAssistant) {
          Message(isAssistant = true, eventTextWithMentionsReplaced)
        } else {
          Message(isAssistant = false, messageText)
        }
      )
      if (channelLastMessages.size > 10) channelLastMessages.removeAt(0)
      LOGGER.debug("channelLastMessages=$channelLastMessages")

      if (event.text.contains("<@${botMember.id}>") && event.user != botMember.id) {
        val botResponse =
          getBotResponse(openAIClient = openAIClient, systemMessage = arguments.systemMessage, channelLastMessages = channelLastMessages)
            .replaceMentionsNameToUserId(allMembers)
        LOGGER.debug("Bot response: $botResponse")
        slackClient.chatPostMessage(event.channel, botResponse)
      }
    }
    LOGGER.debug("Was disconnected, reconnecting...")
    delay(10_000)
  }
}

private suspend fun getBotResponse(openAIClient: OpenAIClient, systemMessage: String, channelLastMessages: List<Message>): String {
  return try {
    openAIClient.chatCompletion(
      model = "gpt-4",
      systemMessage = systemMessage.trim(),
      messages = channelLastMessages.map {
        if (it.isAssistant) {
          OpenAIClient.Message.Assistant(it.text)
        } else {
          OpenAIClient.Message.User(it.text)
        }
      },
    )
  } catch (e: Exception) {
    LOGGER.warn("Could not get chat completion", e)
    null
  }
    ?: "Oops there was a problem :( Check the logs."
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
