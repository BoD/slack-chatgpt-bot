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

package org.jraf.slackchatgptbot.slack.json

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface JsonEvent {
  @Serializable
  @SerialName("message")
  data class JsonMessageEvent(
    val user: String? = null,
    val channel: String,
    val text: String = "",
    val ts: String,
    val subtype: String? = null,
    val previous_message: JsonMessageEditPreviousMessage? = null,
    val message: JsonMessageEditNewMessage? = null,
  ) : JsonEvent {
    @Serializable
    data class JsonMessageEditPreviousMessage(
      val ts: String,
      val user: String,
    )

    @Serializable
    data class JsonMessageEditNewMessage(
      val text: String = "",
    )
  }

  @Serializable
  @SerialName("reaction_added")
  data class JsonReactionAddedEvent(
    val user: String,
    val reaction: String = "",
    val item: JsonReactionItem,
    val event_ts: String,
  ) : JsonEvent {
    @Serializable
    data class JsonReactionItem(
      val type: String,
      val channel: String = "",
      val ts: String = "",
    )
  }

  @Serializable
  data class JsonUnknownEvent(
    val type: String,
  ) : JsonEvent
}
