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

package org.jraf.slackchatgptbot.openai.client

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.jraf.slackchatgptbot.openai.json.chat.completions.JsonChatCompletionsRequest
import org.jraf.slackchatgptbot.openai.json.chat.completions.JsonChatCompletionsResponse
import org.jraf.slackchatgptbot.openai.json.threads.JsonThreadsCreateResponse
import org.jraf.slackchatgptbot.openai.json.threads.JsonThreadsMessage
import org.jraf.slackchatgptbot.openai.json.threads.JsonThreadsMessagesCreateRequest
import org.jraf.slackchatgptbot.openai.json.threads.JsonThreadsRun
import org.jraf.slackchatgptbot.openai.json.threads.JsonThreadsRunsCreateRequest

class OpenAIService(
  private val httpClient: HttpClient,
) {
  companion object {
    private const val URL_BASE = "https://api.openai.com/v1"

    private fun HttpRequestBuilder.assistantsV1Header() {
      header("OpenAI-Beta", "assistants=v1")
    }
  }

  suspend fun chatCompletions(request: JsonChatCompletionsRequest): JsonChatCompletionsResponse {
    return httpClient.post("$URL_BASE/chat/completions") {
      contentType(ContentType.Application.Json)
      setBody(request)
    }.body()
  }

  suspend fun threadsCreate(): JsonThreadsCreateResponse {
    return httpClient.post("$URL_BASE/threads") {
      contentType(ContentType.Application.Json)
      assistantsV1Header()
    }.body()
  }

  suspend fun threadsMessagesCreate(threadId: String, messageCreateRequest: JsonThreadsMessagesCreateRequest): JsonThreadsMessage {
    return httpClient.post("$URL_BASE/threads/$threadId/messages") {
      contentType(ContentType.Application.Json)
      assistantsV1Header()
      setBody(messageCreateRequest)
    }.body()
  }

  suspend fun threadsMessagesList(threadId: String): List<JsonThreadsMessage> {
    return httpClient.post("$URL_BASE/threads/$threadId/messages") {
      contentType(ContentType.Application.Json)
      assistantsV1Header()
    }.body()
  }

  suspend fun threadsRunsCreate(threadId: String, runCreateRequest: JsonThreadsRunsCreateRequest): JsonThreadsRun {
    return httpClient.post("$URL_BASE/threads/$threadId/runs") {
      contentType(ContentType.Application.Json)
      assistantsV1Header()
      setBody(runCreateRequest)
    }.body()
  }

  suspend fun threadsRunsRetrieve(threadId: String, runId: String): JsonThreadsRun {
    return httpClient.post("$URL_BASE/threads/$threadId/runs/$runId") {
      contentType(ContentType.Application.Json)
      assistantsV1Header()
    }.body()
  }
}
