package com.example.ai_companion.news;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DailyNewsStoreTest {
	@TempDir
	Path directory;

	@Test
	void eventsAndIssuesSurviveReloadWithoutDuplicateDays() throws Exception {
		Path file = directory.resolve("daily-news.json");
		DailyNewsStore first = DailyNewsStore.load(file);
		first.record(3, NewsCategory.PLAYER, "Alex 上线");
		first.record(3, NewsCategory.WORLD, "主世界开始下雨");
		DailyNewsIssue issue = first.upsertIssue(3, "测试日报", "正文一");
		DailyNewsIssue updated = first.upsertIssue(3, "测试日报", "正文二");
		assertEquals(issue.id(), updated.id());
		first.saveAiEdition(issue.id(), "AI 编辑版");

		DailyNewsStore second = DailyNewsStore.load(file);
		assertEquals(2, second.eventsForDay(3).size());
		assertEquals(1, second.issues().size());
		assertEquals("AI 编辑版", second.requireIssue(issue.id()).aiEdition());
	}

	@Test
	void newspaperBodyAlwaysContainsAllThreeRequiredSections() {
		List<NewsEvent> events = List.of(
			new NewsEvent(1, 4, 1, NewsCategory.PLAYER, "Steve 进入下界"),
			new NewsEvent(2, 4, 2, NewsCategory.AI, "AI Builder 切换模式"));
		String body = MinecraftDailyNewsManager.buildBody(events);
		assertTrue(body.contains("【玩家事件】"));
		assertTrue(body.contains("【世界事件】\n- 暂无记录"));
		assertTrue(body.contains("【AI事件】"));
	}
}
