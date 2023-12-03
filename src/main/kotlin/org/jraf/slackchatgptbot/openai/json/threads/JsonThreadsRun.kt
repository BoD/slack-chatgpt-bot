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

package org.jraf.slackchatgptbot.openai.json.threads

import kotlinx.serialization.Serializable

@Serializable
data class JsonThreadsRun(
  val id: String,
  val created_at: Long,
  val thread_id: String,
  val status: String,
  val started_at: Long,
  val expires_at: Long?,
  val cancelled_at: Long?,
  val failed_at: Long?,
  val completed_at: Long?,
  val last_error: String?,
) {
  companion object {
    const val STATUS_QUEUED = "queued"
    const val STATUS_IN_PROGRESS = "in_progress"
    const val STATUS_REQUIRES_ACTION = "requires_action"
    const val STATUS_CANCELLING = "cancelling"
    const val STATUS_CANCELLED = "cancelled"
    const val STATUS_FAILED = "failed"
    const val STATUS_COMPLETED = "completed"
    const val STATUS_EXPIRED = "expired"
  }
}
