package com.example.vault.node;

import com.example.vault.exception.ApiException;
import org.springframework.http.HttpStatus;

import java.util.Set;

public final class FolderAppearanceKeys {

    public static final String DEFAULT_ICON = "folder";
    public static final String DEFAULT_COLOR = "default";

    public static final Set<String> ICON_KEYS = Set.of(
            "folder",
            "folder-open",
            "landmark",
            "home",
            "briefcase",
            "users",
            "building-2",
            "file-stack",
            "zap",
            "droplets",
            "shield",
            "hammer",
            "badge-check",
            "heart-pulse",
            "receipt",
            "file-text",
            "stethoscope",
            "pill",
            "syringe",
            "hospital",
            "eye",
            "id-card",
            "lightbulb",
            "star",
            "bookmark",
            "tag",
            "calendar",
            "lock",
            "wrench",
            "car",
            "plane",
            "graduation-cap",
            "wallet",
            "credit-card",
            "scale",
            "microscope",
            "clipboard-list",
            "phone",
            "mail",
            "package",
            "shopping-cart",
            "baby",
            "dog",
            "tree-pine",
            "banknote",
            "circle-dollar-sign",
            "book-open",
            "sparkles"
    );

    public static final Set<String> COLOR_KEYS = Set.of(
            "default",
            "emerald",
            "slate",
            "violet",
            "blue",
            "indigo",
            "amber",
            "yellow",
            "sky",
            "teal",
            "orange",
            "lime",
            "rose",
            "pink"
    );

    private FolderAppearanceKeys() {
    }

    public static String resolveIconKey(String iconKey) {
        if (iconKey == null || iconKey.isBlank()) {
            return DEFAULT_ICON;
        }
        String normalized = iconKey.trim().toLowerCase();
        if (!ICON_KEYS.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_ICON_KEY",
                    "Unknown folder icon: " + iconKey);
        }
        return normalized;
    }

    public static String resolveColor(String color) {
        if (color == null || color.isBlank()) {
            return DEFAULT_COLOR;
        }
        String normalized = color.trim().toLowerCase();
        if (!COLOR_KEYS.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_COLOR",
                    "Unknown folder color: " + color);
        }
        return normalized;
    }
}
