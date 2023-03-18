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

package org.jraf.slackchatgptbot.openai.client

import io.ktor.client.HttpClient
import io.ktor.client.engine.ProxyBuilder
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.DEFAULT
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.URLBuilder
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.jraf.slackchatgptbot.openai.client.configuration.ClientConfiguration
import org.jraf.slackchatgptbot.openai.client.configuration.HttpLoggingLevel
import org.jraf.slackchatgptbot.openai.json.JsonChatCompletionsRequest
import org.jraf.slackchatgptbot.openai.json.JsonMessage

class OpenAIClient(private val clientConfiguration: ClientConfiguration) {
  private val service: OpenAIService by lazy {
    OpenAIService(
      provideHttpClient(clientConfiguration)
    )
  }

  private fun provideHttpClient(clientConfiguration: ClientConfiguration): HttpClient {
    return HttpClient {
      install(ContentNegotiation) {
        json(
          Json {
            ignoreUnknownKeys = true
            useAlternativeNames = false
          }
        )
      }
      install(Auth) {
        bearer {
          loadTokens {
            BearerTokens(clientConfiguration.authBearerToken, "")
          }
        }
      }
      install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 60_000
        socketTimeoutMillis = 60_000
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

  sealed interface Message {
    val content: String

    data class User(override val content: String) : Message
    data class Assistant(override val content: String) : Message
  }

  suspend fun chatCompletion(
    model: String,
    systemMessage: String,
    messages: List<Message>,
  ): String? {
    val chatCompletions = service.chatCompletions(
      JsonChatCompletionsRequest(
        model = model,
        messages = buildList {
          add(JsonMessage(role = JsonMessage.ROLE_SYSTEM, content = systemMessage))
          addAll(
            messages.map {
              JsonMessage(
                role = when (it) {
                  is Message.User -> JsonMessage.ROLE_USER
                  is Message.Assistant -> JsonMessage.ROLE_ASSISTANT
                },
                content = it.content
              )
            }
          )
        }
      )
    )
    return chatCompletions.choices.singleOrNull()?.message?.content
  }
}
