/*
 * This source is part of the
 *      _____  ___   ____
 *  __ / / _ \/ _ | / __/___  _______ _
 * / // / , _/ __ |/ _/_/ _ \/ __/ _ `/
 * \___/_/|_/_/ |_/_/ (_)___/_/  \_, /
 *                              /___/
 * repository.
 *
 * Copyright (C) 2023-present Benoit 'BoD' Lubek (BoD@JRAF.org)
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

package org.jraf.slackchatgptbot.slack.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.http.URLBuilder
import io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jraf.slackchatgptbot.slack.client.configuration.ClientConfiguration
import org.jraf.slackchatgptbot.slack.client.configuration.HttpLoggingLevel
import org.jraf.slackchatgptbot.slack.json.JsonChatPostMessageRequest
import org.jraf.slackchatgptbot.slack.json.JsonEvent
import org.jraf.slackchatgptbot.slack.json.JsonMember
import org.jraf.slackchatgptbot.slack.json.JsonReactionAddRequest
import org.slf4j.LoggerFactory

class SlackClient(private val clientConfiguration: ClientConfiguration) {
  private val LOGGER = LoggerFactory.getLogger(SlackClient::class.java)

  private val service: SlackService by lazy {
    SlackService(
      provideHttpClient(clientConfiguration)
    )
  }

  private fun provideHttpClient(clientConfiguration: ClientConfiguration): HttpClient {
    val json = Json {
      ignoreUnknownKeys = true
      useAlternativeNames = false
      serializersModule = SerializersModule {
        polymorphic(JsonEvent::class) {
          defaultDeserializer { JsonEvent.JsonUnknownEvent.serializer() }
        }
      }
    }
    return HttpClient {
      install(ContentNegotiation) {
        json(json)
      }
      install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 60_000
        socketTimeoutMillis = 60_000
      }
      install(WebSockets) {
        pingInterval = 60_000
        contentConverter = KotlinxWebsocketSerializationConverter(json)
      }
      engine {
        // Setup a proxy if requested
        clientConfiguration.httpConfiguration.httpProxy?.let { httpProxy ->
          proxy = ProxyBuilder.http(URLBuilder().apply {
            host = httpProxy.host
            port = httpProxy.port
          }.build())
        }
      }
      // Setup logging if requested
      if (clientConfiguration.httpConfiguration.loggingLevel != HttpLoggingLevel.NONE) {
        install(Logging) {
          logger = Logger.DEFAULT
          level = when (clientConfiguration.httpConfiguration.loggingLevel) {
            HttpLoggingLevel.NONE -> LogLevel.NONE
            HttpLoggingLevel.INFO -> LogLevel.INFO
            HttpLoggingLevel.HEADERS -> LogLevel.HEADERS
            HttpLoggingLevel.BODY -> LogLevel.BODY
            HttpLoggingLevel.ALL -> LogLevel.ALL
          }
        }
      }
    }
  }

  suspend fun getAllMembers(): List<JsonMember> {
    return try {
      val memberList = mutableListOf<JsonMember>()
      var cursor: String? = null
      do {
        LOGGER.debug("Calling usersList cursor=$cursor")
        val response = service.usersList(botUserOAuthToken = clientConfiguration.botUserOAuthToken, cursor = cursor)
        memberList += response.members
        cursor = response.response_metadata?.next_cursor?.ifBlank { null }
        // Avoid hitting the rate limit
        delay(3000)
      } while (cursor != null)
      memberList
    } catch (e: Exception) {
      LOGGER.warn("Could not make network call", e)
      throw e
    }
  }

  suspend fun appsConnectionsOpen(): String {
    return service.appsConnectionsOpen(clientConfiguration.appToken).url
  }

  suspend fun openWebSocket(url: String, onMessage: suspend (JsonEvent) -> Unit) {
    service.openWebSocket(url, onMessage)
  }

  suspend fun chatPostMessage(channel: String, text: String) {
    service.chatPostMessage(clientConfiguration.botUserOAuthToken, JsonChatPostMessageRequest(channel = channel, text = text))
  }

  suspend fun reactionsAdd(channel: String, timestamp: String, name: String) {
    service.reactionsAdd(
      clientConfiguration.botUserOAuthToken,
      JsonReactionAddRequest(channel = channel, timestamp = timestamp, name = name)
    )
  }
}
