package com.example.ai_companion.client.navigation;

import com.example.ai_companion.navigation.NavigationCatalogPayload;
import com.example.ai_companion.navigation.NavigationCatalogRequestPayload;
import com.example.ai_companion.navigation.NavigationCancelRequestPayload;
import com.example.ai_companion.navigation.NavigationEntry;
import com.example.ai_companion.navigation.NavigationLocateRequestPayload;
import com.example.ai_companion.navigation.NavigationTargetPayload;
import com.example.ai_companion.navigation.NavigationStatePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.List;

/** Owns navigator network state without exposing mutable packet data to screens. */
public final class NavigationClientController {
	private static List<NavigationEntry> entries = List.of();
	private static boolean enabled;
	private static boolean waiting;
	private static String message = "";
	private static int revision;

	private NavigationClientController() {
	}

	public static void initialize() {
		ClientPlayNetworking.registerGlobalReceiver(NavigationCatalogPayload.TYPE, (payload, context) -> {
			entries = payload.entries();
			enabled = payload.enabled();
			waiting = false;
			message = payload.message();
			revision++;
		});
		ClientPlayNetworking.registerGlobalReceiver(NavigationTargetPayload.TYPE, (payload, context) -> {
			waiting = false;
			message = payload.message();
			if (payload.success()) NavigationHud.setTarget(payload);
		});
		ClientPlayNetworking.registerGlobalReceiver(NavigationStatePayload.TYPE, (payload, context) -> {
			message = payload.message();
			waiting = false;
			if (!payload.active()) NavigationHud.clear();
			revision++;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> clear());
	}

	public static void requestCatalog() {
		waiting = true;
		message = "正在读取服务器注册表……";
		if (!ClientPlayNetworking.canSend(NavigationCatalogRequestPayload.TYPE)) {
			waiting = false;
			message = "服务器不支持结构与群系导航";
			return;
		}
		ClientPlayNetworking.send(new NavigationCatalogRequestPayload());
	}

	public static void locate(NavigationEntry entry, boolean teleport) {
		waiting = true;
		message = teleport ? "正在验证传送权限并搜索……" : "正在搜索目标……";
		ClientPlayNetworking.send(new NavigationLocateRequestPayload(entry.type(), entry.id(), teleport));
	}

	public static void cancel() {
		waiting = true;
		message = "正在回收临时导航物品……";
		if (!ClientPlayNetworking.canSend(NavigationCancelRequestPayload.TYPE)) {
			waiting = false;
			message = "服务器不支持导航清理";
			return;
		}
		ClientPlayNetworking.send(new NavigationCancelRequestPayload());
	}

	public static List<NavigationEntry> entries() { return entries; }
	public static boolean enabled() { return enabled; }
	public static boolean waiting() { return waiting; }
	public static String message() { return message; }
	public static int revision() { return revision; }

	public static void open(Minecraft client, net.minecraft.client.gui.screens.Screen parent) {
		requestCatalog();
		client.setScreenAndShow(new NavigatorScreen(parent));
	}

	private static void clear() {
		entries = List.of(); enabled = false; waiting = false; message = ""; revision++;
		NavigationHud.clear();
	}
}
