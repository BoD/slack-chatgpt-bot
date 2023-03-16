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
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import org.jraf.slackchatgptbot.openai.json.JsonChatCompletionsRequest
import org.jraf.slackchatgptbot.openai.json.JsonChatCompletionsResponse

class OpenAIService(
  private val httpClient: HttpClient,
) {
  companion object {
    private const val URL_BASE = "https://api.openai.com/v1"
  }

  suspend fun chatCompletions(request: JsonChatCompletionsRequest): JsonChatCompletionsResponse {
    return httpClient.post("$URL_BASE/chat/completions") {
      contentType(ContentType.Application.Json)
      setBody(request)
    }.body()
  }
}
