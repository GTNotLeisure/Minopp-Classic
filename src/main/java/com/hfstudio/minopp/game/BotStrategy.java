package com.hfstudio.minopp.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

public class BotStrategy {

    private final CardGame game;
    private final CardPlayer player;
    private final List<Hand> hands = new ArrayList<>();
    private final Hand own;
    private final Object2IntMap<CardFace> normalScores = new Object2IntOpenHashMap<>();
    private final Object2IntMap<CardFace> flexScores = new Object2IntOpenHashMap<>();

    public BotStrategy(CardGame game, CardPlayer player) {
        this.game = Objects.requireNonNull(game, "game");
        this.player = Objects.requireNonNull(player, "player");
        if (game.tableEntity != null && game.tableEntity.getWorldObj() != null
            && game.tableEntity.getWorldObj().isRemote)
            throw new IllegalStateException("Bot strategies require server-side hands");
        own = new Hand(game, player);
        for (CardPlayer member : game.players) {
            if (!member.eliminated && !member.equals(player)) hands.add(new Hand(game, member));
        }
    }

    public static int openingCardIndex(CardGame game, CardPlayer player) {
        int top = game.deck.size() - 1;
        if (game.debug || BotDifficulty.forPlayer(game, player) != BotDifficulty.HIGH) return top;
        int best = top, bestScore = Integer.MIN_VALUE;
        for (int index = top; index >= Math.max(0, top - 5); index--) {
            Card card = game.deck.get(index);
            int score = openingQuality(card.light, player.hand, false);
            if (card.dark != null) score += openingQuality(card.dark, player.hand, true) / 3;
            if (score > bestScore) {
                bestScore = score;
                best = index;
            }
        }
        return best;
    }

    private static int openingQuality(CardFace face, List<Card> hand, boolean dark) {
        CardType type = face.type();
        int score = face.isWild() ? 35 : face.isNumber() ? 4 : 12;
        if (type.isDraw()) score += 8 + Math.min(10, type.drawValue);
        if (type.fast || type.pick != 0) score += 12;
        int matches = 0, wilds = 0;
        for (Card held : hand) {
            CardFace other = held.face(dark);
            if (other.isWild()) wilds++;
            if (!face.isWild() && face.color == other.color) matches++;
        }
        return score + Math.min(15, matches * 3) - (face.isWild() ? wilds * 10 : 0);
    }

    public int score(Card card, boolean useFlex, int baseScore) {
        CardFace face = game.face(card);
        Object2IntMap<CardFace> cache = useFlex ? flexScores : normalScores;
        if (!cache.containsKey(face)) cache.put(face, adjustment(card, useFlex));
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, (long) baseScore + cache.getInt(face)));
    }

    private int adjustment(Card card, boolean useFlex) {
        CardFace face = game.face(card);
        CardType type = face.type();
        if (player.hand.size() == 1) return 10_000;
        CardColor color = colorFor(card, useFlex);
        int score = Math.min(50, game.cardPoints(card)) / 3;
        score += 8 * remainingColor(color, card);
        score += 45 * type.pickCount(player.hand.size() - 1);
        if (type == CardTypes.DISCARD_ALL || type == CardTypes.DOUBLE_DISCARD_ALL)
            score += 45 * remainingColor(face.color, card);
        if (type == CardTypes.WILD_DISCARD_ALL || type == CardTypes.DOUBLE_DISCARD_ALL)
            score += 45 * remainingColor(color, card);
        if (type.fast || type == CardTypes.SKIP_EVERYONE || useFlex && type == CardTypes.FLEX_SKIP) score += 35;
        if (face.isWild()) score -= 25;
        if (useFlex) score -= 5;
        if (type == CardTypes.TRADE_HANDS || type == CardTypes.WILD_SWAP_HANDS
            || face.isNumber() && face.number == 7
                && game.config()
                    .getBool(GameRules.SEVEN_ZERO)) {
            int smallest = player.hand.size();
            for (Hand other : hands) if (!allied(other.player)) smallest = Math.min(smallest, other.player.hand.size());
            score += (player.hand.size() - 1 - smallest) * 45;
        }
        CardPlayer next = successor(type, useFlex);
        Hand target = hand(next);
        if (target != null) {
            boolean ally = allied(next);
            int weight = Math.max(1, 6 - Math.min(5, next.hand.size()));
            int replies = replies(target, face, color, type.isDraw() && !useFlex);
            score += (ally ? 1 : -1) * (replies == 0 ? -12 * weight : Math.min(6, replies) * weight);
            if (type.isDraw()) score += (ally ? -1 : 1) * (10 + Math.min(20, type.drawValue) + weight * 4);
        }
        CardPlayer ordinaryNext = game.nextPlayer();
        if (!next.equals(ordinaryNext) && !allied(ordinaryNext))
            score += Math.max(0, 5 - ordinaryNext.hand.size()) * 16;
        if (type == CardTypes.FLIP) {
            score += own.reverseStrength - own.strength;
            for (Hand other : hands) score += (allied(other.player) ? 1 : -1) * (other.reverseStrength - other.strength)
                / Math.max(1, hands.size());
        }
        if (type.matchKey.equals("wild_draw4") && game.config()
            .getBool(GameRules.CHALLENGE) && !useFlex && remainingColor(game.currentColor, card) > 0) score -= 90;
        return score;
    }

    public void choose(Card card, PlayContext.PlayChoices choices) {
        CardType type = game.face(card)
            .type();
        CardType.Choice required = type.choiceFor(choices.useFlex);
        if (required.needsColor()) choices.color = colorFor(card, choices.useFlex);
        if (required.targetCount() > 0) {
            List<Hand> targets = new ArrayList<>(hands);
            targets.sort(
                Comparator.comparingInt(this::threat)
                    .reversed());
            choices.targets.clear();
            for (int i = 0; i < Math.min(required.targetCount(), targets.size()); i++)
                choices.targets.add(targets.get(i).player.uuid);
        }
        if (required.needsNumber()) {
            int best = Integer.MIN_VALUE;
            Hand next = hand(game.nextPlayer());
            for (int number = 1; number <= 9; number++) {
                int value = own.numbers[number] * 12;
                if (next != null) value += (allied(next.player) ? 1 : -1) * next.numbers[number] * 6;
                if (value > best) {
                    best = value;
                    choices.number = number;
                }
            }
        }
        int count = type.pickCount(player.hand.size() - 1);
        if (count > 0) {
            List<Card> candidates = new ArrayList<>(player.hand);
            candidates.removeIf(candidate -> candidate.uid == card.uid);
            candidates.sort(
                Comparator.comparingInt(this::discardValue)
                    .reversed());
            choices.picks.clear();
            for (int i = 0; i < Math.min(count, candidates.size()); i++) choices.picks.add(candidates.get(i).uid);
        }
    }

    private int discardValue(Card card) {
        CardFace face = game.face(card);
        return Math.min(200, game.cardPoints(card)) - (face.isWild() ? 30 : own.colors[face.color.ordinal()] * 3);
    }

    private CardColor colorFor(Card card, boolean useFlex) {
        CardFace face = game.face(card);
        CardType type = face.type();
        if (!type.choiceFor(useFlex)
            .needsColor())
            return type.keepsColor ? game.currentColor
                : useFlex && face.flexColor != null ? face.flexColor : face.color;
        CardColor best = game.colors()
            .get(0);
        int bestScore = Integer.MIN_VALUE;
        CardPlayer next = successor(type, useFlex);
        for (CardColor color : game.colors()) {
            int score = remainingColor(color, card)
                * (type == CardTypes.WILD_DISCARD_ALL || type == CardTypes.DOUBLE_DISCARD_ALL ? 50 : 10);
            if (type == CardTypes.DOUBLE_DISCARD_ALL && color == face.color) score -= 1000;
            for (Hand other : hands) {
                int count = other.colors[color.ordinal()] + other.wilds;
                int weight = Math.max(1, 6 - Math.min(5, other.player.hand.size()));
                if (other.player.equals(next)) weight *= 3;
                score += (allied(other.player) ? 1 : -1) * (count == 0 ? -4 : Math.min(4, count)) * weight;
            }
            if (score > bestScore) {
                bestScore = score;
                best = color;
            }
        }
        return best;
    }

    private int replies(Hand target, CardFace played, CardColor color, boolean draw) {
        int count = 0;
        for (Object2IntMap.Entry<CardFace> entry : target.faces.object2IntEntrySet()) {
            CardFace face = entry.getKey();
            CardType type = face.type();
            boolean matchesColor = color == CardColor.WILD || face.color == color
                || target.flex && face.flexColor == color;
            boolean matchesSymbol = (!played.isWild() || played.type().keepsColor
                || !played.type().matchAliases.isEmpty()) && face.sharesSymbol(played);
            boolean playable;
            if (draw) {
                boolean stack = game.config()
                    .getBool(GameRules.STACKING) && type.isDraw()
                    && ((game.config()
                        .getBool(GameRules.STACK_ANY_DRAW)
                        || game.config()
                            .getBool(GameRules.STACK_DRAW4_ON_DRAW2)) ? type.drawValue >= played.type().drawValue
                                : type.drawValue == played.type().drawValue);
                playable = type.answersDraw || stack
                    || game.config()
                        .getBool(GameRules.DEFLECT_DRAW)
                        && (type.matchKey.equals("skip") || type.matchKey.equals("reverse"))
                        && (face.isWild() || matchesColor || matchesSymbol);
            } else playable = face.isWild() || matchesColor || matchesSymbol;
            if (playable) count += entry.getIntValue();
        }
        return count;
    }

    private int remainingColor(CardColor color, Card played) {
        CardFace face = game.face(played);
        return own.colors[color.ordinal()] - (face.color == color || own.flex && face.flexColor == color ? 1 : 0);
    }

    private int threat(Hand hand) {
        return (allied(hand.player) ? -10_000 : 0) + 1000 / (1 + hand.player.hand.size()) + Math.min(80, hand.strength);
    }

    private boolean allied(CardPlayer other) {
        return other.equals(player) || player.team >= 0 && player.team == other.team;
    }

    private Hand hand(CardPlayer member) {
        for (Hand hand : hands) if (hand.player.equals(member)) return hand;
        return null;
    }

    private CardPlayer successor(CardType type, boolean useFlex) {
        if (type.fast || type == CardTypes.SKIP_EVERYONE
            || type == CardTypes.BONUS_TURN
            || type == CardTypes.PLAY_AGAIN_10
            || useFlex && type == CardTypes.FLEX_SKIP) return player;
        boolean reverse = type.matchKey.equals("reverse") || type == CardTypes.AW_REVERSE
            || type == CardTypes.WILD_REVERSE
            || type == CardTypes.WILD_REVERSE_DRAW4
            || type == CardTypes.WILD_REVERSE_DRAW8
            || type == CardTypes.WILD_REVERSE_PLUS6
            || type == CardTypes.REVERSE_DRAW2
            || type == CardTypes.REVERSE_SKIP;
        int distance = type.matchKey.equals("skip") || type == CardTypes.WILD_SKIP
            || type == CardTypes.AW_SKIP
            || useFlex && type == CardTypes.FLEX_REVERSE ? 2 : 1;
        if (type == CardTypes.AW_SKIP_TWO) distance = 3;
        if (reverse && hands.size() == 1) distance++;
        int direction = game.isAntiClockwise != reverse ? -1 : 1;
        int index = game.players.indexOf(player);
        for (int moved = 0, scanned = 0; moved < distance && scanned < game.players.size() * distance; scanned++) {
            index = Math.floorMod(index + direction, game.players.size());
            if (!game.players.get(index).eliminated) moved++;
        }
        return game.players.get(index);
    }

    private static class Hand {

        private final CardPlayer player;
        private final Object2IntMap<CardFace> faces = new Object2IntOpenHashMap<>();
        private final int[] colors = new int[CardColor.values().length];
        private final int[] numbers = new int[10];
        private final boolean flex;
        private int wilds, strength, reverseStrength;

        private Hand(CardGame game, CardPlayer player) {
            this.player = player;
            flex = player.powerOn && game.config()
                .getBool(GameRules.FLEX_POWER);
            for (Card card : player.hand) {
                CardFace face = game.face(card);
                if (face.isHidden()) continue;
                faces.mergeInt(face, 1, Integer::sum);
                colors[face.color.ordinal()]++;
                if (face.isWild()) wilds++;
                if (flex && face.flexColor != null && face.flexColor != face.color) colors[face.flexColor.ordinal()]++;
                if (face.isNumber() && face.number >= 0 && face.number < numbers.length) numbers[face.number]++;
                strength += face.isWild() ? 8
                    : face.type()
                        .isDraw() ? 4 : 1;
                CardFace back = card.face(!game.darkSide);
                if (!back.isHidden()) reverseStrength += back.isWild() ? 8
                    : back.type()
                        .isDraw() ? 4 : 1;
            }
        }
    }
}
