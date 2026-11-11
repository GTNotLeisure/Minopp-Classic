package com.hfstudio.minopp.game;

public enum BotDifficulty {

    LOW,
    MEDIUM,
    HIGH;

    public static BotDifficulty forPlayer(CardGame game, CardPlayer player) {
        if (player == null || !player.bot) return MEDIUM;
        return switch (game.config()
            .getInt(GameRules.BOT_DIFFICULTY)) {
            case 0 -> LOW;
            case 2 -> HIGH;
            default -> MEDIUM;
        };
    }
}
