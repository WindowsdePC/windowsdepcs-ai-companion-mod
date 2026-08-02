package com.example.ai_companion.ai;

/** One allow-listed action produced by an AI provider. */
public record AiDecision(String action, String say, double dx, double dz) {
	public AiDecision sanitized() {
		String selected = action == null ? "wait" : action.toLowerCase();
		if (!selected.equals("say") && !selected.equals("move") && !selected.equals("wait")) {
			selected = "wait";
		}
		String message = say == null ? "" : say.strip();
		if (message.length() > 240) message = message.substring(0, 240);
		return new AiDecision(selected, message, Math.clamp(dx, -8, 8), Math.clamp(dz, -8, 8));
	}
}
