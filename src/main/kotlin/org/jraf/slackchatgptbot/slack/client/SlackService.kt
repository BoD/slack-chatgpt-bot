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

package org.jraf.slackchatgptbot.slack.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.websocket.receiveDeserialized
import io.ktor.client.plugins.websocket.sendSerialized
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import org.jraf.slackchatgptbot.slack.json.JsonAcknowledge
import org.jraf.slackchatgptbot.slack.json.JsonAppsConnectionsOpenResponse
import org.jraf.slackchatgptbot.slack.json.JsonChatPostMessageRequest
import org.jraf.slackchatgptbot.slack.json.JsonEvent
import org.jraf.slackchatgptbot.slack.json.JsonPayloadEnvelope
import org.jraf.slackchatgptbot.slack.json.JsonReactionAddRequest
import org.jraf.slackchatgptbot.slack.json.JsonUsersListResponse
import org.slf4j.LoggerFactory

class SlackService(
  private val httpClient: HttpClient,
) {
  companion object {
    private const val URL_BASE = "https://slack.com/api"
  }

  private val LOGGER = LoggerFactory.getLogger(SlackClient::class.java)

  // https://api.slack.com/methods/apps.connections.open
  suspend fun appsConnectionsOpen(appToken: String): JsonAppsConnectionsOpenResponse {
    return httpClient.post("$URL_BASE/apps.connections.open") {
      header("Authorization", "Bearer $appToken")
      contentType(ContentType.Application.Json)
    }.body()
  }

  // https://api.slack.com/methods/users.list
  suspend fun usersList(botUserOAuthToken: String, cursor: String? = null, limit: Int = 1000): JsonUsersListResponse {
    return httpClient.get("$URL_BASE/users.list") {
      header("Authorization", "Bearer $botUserOAuthToken")
      parameter("cursor", cursor)
      parameter("limit", limit)
      contentType(ContentType.Application.Json)
    }.body()
  }

  // https://api.slack.com/methods/chat.postMessage
  suspend fun chatPostMessage(botUserOAuthToken: String, request: JsonChatPostMessageRequest) {
    httpClient.post("$URL_BASE/chat.postMessage") {
      header("Authorization", "Bearer $botUserOAuthToken")
      contentType(ContentType.Application.Json)
      setBody(request)
    }
  }

  // https://api.slack.com/methods/reactions.add
  suspend fun reactionsAdd(botUserOAuthToken: String, request: JsonReactionAddRequest) {
    httpClient.post("$URL_BASE/reactions.add") {
      header("Authorization", "Bearer $botUserOAuthToken")
      contentType(ContentType.Application.Json)
      setBody(request)
    }
  }

  suspend fun openWebSocket(url: String, onMessage: suspend (JsonEvent) -> Unit) {
    httpClient.webSocket(url) {
      // Ignore hello message
      val helloMessage = incoming.receive() as Frame.Text
      LOGGER.info("WebSocket in: ${helloMessage.readText()}")
      while (true) {
        val payloadEnvelope = try {
          receiveDeserialized<JsonPayloadEnvelope>()
        } catch (e: Exception) {
          LOGGER.error("Error while receiving message", e)
          break
        }
        LOGGER.info("WebSocket in: $payloadEnvelope")
        val ack = JsonAcknowledge(envelope_id = payloadEnvelope.envelope_id)
        LOGGER.info("WebSocket out: $ack")
        sendSerialized(ack)
        onMessage(payloadEnvelope.payload.event)
      }
    }
  }

}
