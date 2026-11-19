package com.clippost.app

/** A YouTube error turned into plain words, plus whether trying again later can help. */
data class Explained(val message: String, val retryable: Boolean)

object YouTubeErrors {

    /** Google's page about quota and the API compliance audit. */
    const val AUDIT_HELP_URL = "https://developers.google.com/youtube/v3/guides/quota_and_compliance_audits"

    /**
     * @param code the HTTP status, e.g. 403
     * @param reason YouTube's short reason, e.g. "quotaExceeded" (may be empty)
     * @param detail YouTube's English message (may be empty)
     */
    fun explain(code: Int, reason: String, detail: String): Explained {
        val tag = "[$code" + (if (reason.isNotEmpty()) " $reason" else "") + "]"
        val lower = detail.lowercase()

        return when {
            // Daily quota used up: waiting is the only fix, so don't retry.
            reason == "quotaExceeded" ||
                (reason == "rateLimitExceeded" && ("per day" in lower || "quota" in lower)) ->
                Explained(
                    "Today's YouTube quota for ClipPost is used up. It resets at midnight Pacific Time " +
                        "(${Quota.resetText()} for you). One upload costs about ${Quota.UPLOAD_COST} of " +
                        "${Quota.DAILY_UNITS} daily units, so roughly ${Quota.UPLOADS_PER_DAY} uploads a day. " +
                        "Try again after the reset. $tag",
                    retryable = false
                )

            // Too many requests in a short time: a short wait works.
            reason == "rateLimitExceeded" ->
                Explained("YouTube says there were too many requests at once. ClipPost will try again shortly. $tag", true)

            reason == "uploadLimitExceeded" ->
                Explained("Your channel reached YouTube's daily upload limit. Try again in about 24 hours. $tag", false)

            reason == "youtubeSignupRequired" || reason == "channelNotFound" ->
                Explained("This Google account has no YouTube channel. Create one at youtube.com, then try again. $tag", false)

            reason == "accessNotConfigured" || "has not been used in project" in lower || "is disabled" in lower ->
                Explained(
                    "The YouTube Data API v3 is not turned on for your Google Cloud project. " +
                        "Enable it in APIs & Services > Library, wait a minute, then retry. $tag",
                    false
                )

            code == 401 || reason == "authError" || reason == "unauthorized" ->
                Explained("Your Google sign-in expired or was removed. Open ClipPost, tap Connect, then retry. $tag", false)

            reason == "insufficientPermissions" || "insufficient authentication scopes" in lower ->
                Explained("ClipPost is missing permission to upload. Tap Connect and allow video uploads. $tag", false)

            reason == "invalidTitle" ->
                Explained("YouTube rejected the title. Shorten it or remove special characters, then retry. $tag", false)

            reason == "invalidDescription" ->
                Explained("YouTube rejected the caption. Shorten it or remove special characters, then retry. $tag", false)

            reason == "invalidPublishAt" ->
                Explained("YouTube rejected the schedule time. Pick a later time and retry. $tag", false)

            reason == "forbidden" || code == 403 ->
                Explained(
                    "YouTube refused this upload. The account may not be allowed to upload, or the channel " +
                        "needs verification. ${detail.ifEmpty { "No details given." }} $tag",
                    false
                )

            // Problems on YouTube's side or a timeout usually pass.
            code == 408 || code >= 500 ->
                Explained("YouTube had a problem on its side. ClipPost will try again shortly. $tag", true)

            code == 400 ->
                Explained("YouTube rejected the request: ${detail.ifEmpty { "no details given" }} $tag", false)

            else ->
                Explained("YouTube error: ${detail.ifEmpty { "no details given" }} $tag", false)
        }
    }
}
