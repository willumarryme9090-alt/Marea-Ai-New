package com.marea.ai

import com.marea.ai.ai.ChatCompletionsService
import com.marea.ai.ai.UnconfiguredAiService
import com.marea.ai.avatar.AvatarState
import com.marea.ai.avatar.resolveAvatarState
import com.marea.ai.data.AppSettings
import com.marea.ai.data.ChatMessage
import com.marea.ai.data.ConversationRepository
import com.marea.ai.data.Role
import com.marea.ai.data.normalizeInput
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AvatarStateTest {
    @Test fun idleWhenNothingActive() =
        assertEquals(AvatarState.IDLE, resolveAvatarState(false, false, false, false))

    @Test fun listeningHasPriority() =
        assertEquals(AvatarState.LISTENING, resolveAvatarState(true, true, true, true))

    @Test fun speakingBeatsThinking() =
        assertEquals(AvatarState.SPEAKING, resolveAvatarState(false, true, true, false))

    @Test fun thinkingWhenRequestInFlight() =
        assertEquals(AvatarState.THINKING, resolveAvatarState(false, true, false, false))

    @Test fun errorOnlyWhenNothingElseActive() {
        assertEquals(AvatarState.ERROR, resolveAvatarState(false, false, false, true))
        assertEquals(AvatarState.THINKING, resolveAvatarState(false, true, false, true))
    }

    @Test fun neutralResetAfterSpeechEnds() =
        assertEquals(AvatarState.IDLE, resolveAvatarState(false, false, false, false))
}

class InputTest {
    @Test fun emptyAndBlankMessagesRejected() {
        assertNull(normalizeInput(""))
        assertNull(normalizeInput("   \n "))
    }

    @Test fun messageIsTrimmed() = assertEquals("hello", normalizeInput("  hello "))
}

class AiServiceTest {
    @Test fun unconfiguredServiceFailsHonestly() = runBlocking {
        val r = UnconfiguredAiService().reply(emptyList())
        assertTrue(r.isFailure)
        assertTrue(r.exceptionOrNull()!!.message!!.contains("No AI provider"))
    }

    @Test fun nonHttpsUrlIsRejected() = runBlocking {
        val svc = ChatCompletionsService(AppSettings(baseUrl = "http://example.com", model = "m"))
        assertTrue(svc.reply(emptyList()).isFailure)
    }

    @Test fun parsesValidReply() {
        val body = """{"choices":[{"message":{"role":"assistant","content":" Hi there "}}]}"""
        assertEquals("Hi there", ChatCompletionsService.parseReply(body).getOrNull())
    }

    @Test fun invalidResponseIsAnError() {
        assertTrue(ChatCompletionsService.parseReply("not json").isFailure)
        assertTrue(ChatCompletionsService.parseReply("""{"choices":[]}""").isFailure)
    }

    @Test fun requestBodyContainsModelAndMessages() {
        val s = AppSettings(baseUrl = "https://x", model = "my-model", systemPrompt = "sys")
        val body = ChatCompletionsService.buildRequestBody(s, listOf(ChatMessage(1, Role.USER, "hey")))
        assertTrue(body.contains("my-model"))
        assertTrue(body.contains("hey"))
        assertTrue(body.contains("sys"))
    }
}

class ConversationRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun repo(): ConversationRepository = ConversationRepository(File(tmp.root, "history.json"))

    @Test fun savesAndLoadsRoundTrip() {
        val r = repo()
        r.save(listOf(ChatMessage(1, Role.USER, "a", 5), ChatMessage(2, Role.ASSISTANT, "b", 6)))
        val loaded = repo().load()
        assertEquals(2, loaded.size)
        assertEquals("b", loaded[1].text)
        assertEquals(Role.ASSISTANT, loaded[1].role)
    }

    @Test fun clearRemovesHistory() {
        val r = repo()
        r.save(listOf(ChatMessage(1, Role.USER, "a")))
        r.clear()
        assertTrue(r.load().isEmpty())
    }

    @Test fun corruptedFileDoesNotCrash() {
        File(tmp.root, "history.json").writeText("{{{ broken")
        assertTrue(repo().load().isEmpty())
    }

    @Test fun keepsOnlyLatestMessages() {
        val r = ConversationRepository(File(tmp.root, "h.json"), maxMessages = 3)
        r.save((1..10).map { ChatMessage(it.toLong(), Role.USER, "m$it") })
        assertEquals(listOf("m8", "m9", "m10"), r.load().map { it.text })
    }
}
