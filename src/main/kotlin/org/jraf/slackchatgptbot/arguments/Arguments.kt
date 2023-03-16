/*
 * This source is part of the
 *      _____  ___   ____
 *  __ / / _ \/ _ | / __/___  _______ _
 * / // / , _/ __ |/ _/_/ _ \/ __/ _ `/
 * \___/_/|_/_/ |_/_/ (_)___/_/  \_, /
 *                              /___/
 * repository.
 *
 * Copyright (C) 2021-present Benoit 'BoD' Lubek (BoD@JRAF.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jraf.slackchatgptbot.arguments

import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.cli.ExperimentalCli
import kotlinx.cli.required

class Arguments(av: Array<String>) {
  private val parser = ArgParser("slack-chatgpt-bot")

  val openAiAuthToken: String by parser.option(
    type = ArgType.String,
    fullName = "openai-auth-token",
    shortName = "o",
    description = "OpenAI bearer token"
  ).required()

  val slackAppToken: String by parser.option(
    type = ArgType.String,
    fullName = "slack-app-token",
    shortName = "a",
    description = "Slack app token (starts with xoxb-)"
  ).required()

  val slackBotUserOAuthToken: String by parser.option(
    type = ArgType.String,
    fullName = "slack-bot-user-oauth-token",
    shortName = "u",
    description = "Slack bot user OAuth token (starts with xapp-)"
  ).required()

  val botName: String by parser.option(
    type = ArgType.String,
    fullName = "bot-name",
    shortName = "n",
    description = "The bot's name"
  ).required()

  val systemMessage: String by parser.option(
    type = ArgType.String,
    fullName = "system-message",
    shortName = "m",
    description = "The System Message to pass to OpenAI"
  ).required()

  init {
    parser.parse(av)
  }
}
