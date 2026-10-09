package com.creatoros.script.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * The structured JSON shape of an AI-generated script.
 * This is what gets stored in the "content" JSONB column.
 *
 * ── Why this exact structure? ────────────────────────────────────────────────
 * Short-form video has a proven formula:
 *
 *   HOOK (0-3s):     Grab attention immediately — first 3 seconds decide if people scroll
 *   SECTIONS (body): Each section = one idea + what the camera shows + how to edit it
 *   CTA (last 5s):   Call-to-action — what should the viewer do next?
 *
 * By storing this as structured JSON (not just a text blob), the clip detection
 * algorithm can later identify which sections to extract as highlights.
 *
 * ── Jackson @JsonProperty ────────────────────────────────────────────────────
 * Records use camelCase Java names but the AI returns snake_case JSON.
 * @JsonProperty maps "editing_instruction" → editingInstruction.
 *
 * ── Java Records as nested types ─────────────────────────────────────────────
 * Records can be nested. ScriptContent contains a List<Section>, where
 * Section is also a record. Jackson handles nested record serialization automatically.
 */
public record ScriptContent(

        /**
         * The opening hook — must stop the scroll.
         * e.g. "Did you know most developers fail at system design because of this ONE thing?"
         */
        String hook,

        /**
         * Ordered sections of the script body.
         * Each has a time range, dialogue, visual direction, and editing notes.
         */
        List<Section> sections,

        /**
         * Call-to-action at the end.
         * e.g. "Follow for more tutorials. Link to full course in bio."
         */
        String cta

) {
    /**
     * One section of the script — typically 10-30 seconds.
     */
    public record Section(

            /**
             * Section start time in seconds from video beginning.
             * e.g. 0, 15, 30
             */
            Integer start,

            /**
             * Section end time in seconds.
             */
            Integer end,

            /**
             * What the person says. Written for speech, not reading.
             * e.g. "Spring Boot auto-configures almost everything. Here's what that means..."
             */
            String dialogue,

            /**
             * What the camera or screen shows during this section.
             * e.g. "Code editor showing SecurityConfig.java with filter chain highlighted"
             */
            String visual,

            /**
             * Instructions for the video editor.
             * e.g. "Jump cut every 3 seconds. Add zoom on the highlighted line."
             */
            @JsonProperty("editing_instruction")
            String editingInstruction

    ) {}
}
