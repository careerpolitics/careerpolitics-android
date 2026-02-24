@file:Suppress("TooGenericExceptionCaught", "SwallowedException", "UnusedPrivateProperty")

package com.murari.careerpolitics.services

import org.json.JSONArray
import org.json.JSONObject

/**
 * Enum class representing different categories of notifications.
 * Each category has a machine-readable key and a human-readable display name.
 */
enum class NotificationCategory(val channelId: String, val channelName: String) {
    COMMENT("comment_replies", "Comment Replies"),
    MENTION("mentions", "Mentions"),
    REACTION("reactions", "Reactions"),
    FOLLOWER("followers", "Followers"),
    ACHIEVEMENT("achievements", "Achievements"),
    MILESTONE("milestones", "Milestones"),
    DEFAULT("default", "Default");

    companion object {
        /**
         * Safely parse a category from a string key.
         * Defaults to [DEFAULT] if the key is unknown or null.
         */
        fun fromString(value: String?): NotificationCategory {
            return when (value?.lowercase()) {
                "comment","comment_replies" -> COMMENT
                "mention","mentions" -> MENTION
                "reaction","reactions" -> REACTION
                "follower","followers" -> FOLLOWER
                "achievement","achievements" -> ACHIEVEMENT
                "milestone","milestones" -> MILESTONE
                else -> DEFAULT
            }
        }
    }
}


data class NotificationAction(
    val id: String,
    val label: String,
    val type: String,
    val icon: String? = null
) {
    companion object {
        fun fromJson(json: JSONObject): NotificationAction {
            return NotificationAction(
                id = json.optString("id",""),
                label = json.optString("label",""),
                type = json.optString("type","button"),
                icon = json.optString("icon", null)
            )
        }
    }
}






data class Actor(
    val id: String,
    val username: String,
    val name: String,
    val avatarUrl: String? = null
) {
    companion object {
        fun fromJson(json: JSONObject?): Actor? {
            if (json == null) return null

            return Actor(
                id = json.optString("id",null),
                username = json.optString("username",""),
                name = json.optString("name",""),
                avatarUrl = json.optString("avatar_url", null)
            )
        }
    }
}

data class Target(
    val id: String,
    val type: String,
    val title: String?,
    val url: String?
) {
    companion object {
        fun fromJson(json: JSONObject?): Target? {
            if (json == null) return null

            return Target(
                id = json.optString("id",""),
                type = json.optString("type",""),
                title = json.optString("title", null),
                url = json.optString("url", null)
            )
        }
    }
}


data class NotificationData(
    val notificationType: String,
    val category: NotificationCategory,
    val priority: String,
    val title: String,
    val body: String,
    val actor: Actor?,
    val target: Target?,
    val actionContext: String?,
    val groupKey: String?,
    val color: String?,
    val actions: List<NotificationAction>,
    val url: String?
) {
    companion object {

        fun fromDataPayload(data: Map<String, String>?): NotificationData? {
            if (data.isNullOrEmpty()) return null

            // -------------------------
            // Core fields
            // -------------------------
            val notificationType = data["notification_type"] ?: "default"
            val category = NotificationCategory.fromString(notificationType)

            val priority = data["priority"] ?: "normal"
            val title = data["title"].orEmpty()
            val body = data["body"].orEmpty()

            val groupKey = data["group_key"]
            val color = data["color"]
            val url = data["url"]

            // -------------------------
            // Actor
            // -------------------------
            val actor = try {
                data["actor"]?.let { actorJson ->
                    val json = JSONObject(actorJson)
                    Actor(
                        id = json.optString("id",null),
                        username = json.optString("username"),
                        name = json.optString("name"),
                        avatarUrl = json.optString("avatar_url", null)
                    )
                }
            } catch (e: Exception) {
                null
            }

            // -------------------------
            // Target
            // -------------------------
            val target = try {
                data["target"]?.let {
                    val json = JSONObject(it)

                    Target(
                        id = json.optString("id",null),
                        type = json.optString("type", null),
                        title = json.optString("title", null),
                        url = json.optString("url", null)
                    )
                }
            } catch (e: Exception) {
                null
            }

            // -------------------------
            // Actions
            // -------------------------
            val actions = try {
                data["actions"]?.let { actionsJson ->
                    val jsonArray= JSONArray(actionsJson)
                    (0 until jsonArray.length()).mapNotNull { i->
                        try {
                            val actionJson= jsonArray.getJSONObject(i)
                            NotificationAction(
                                id= actionJson.getString("id"),
                                label= actionJson.getString("label"),
                                type= actionJson.getString("type"),
                                icon = actionJson.optString("icon", null)
                            )
                        } catch (e: Exception) {
                            null
                        }
                    }

                } ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }

            return NotificationData(
                notificationType = notificationType,
                category = category,
                priority = priority,
                title = title,
                body = body,
                actor = actor,
                target = target,
                actionContext = data["action_context"],
                groupKey = groupKey,
                color = color,
                actions = actions,
                url = url
            )
        }
    }
}



