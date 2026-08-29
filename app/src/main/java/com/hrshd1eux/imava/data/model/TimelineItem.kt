package com.hrshd1eux.imava.data.model

sealed interface TimelineItem {
    data class Header(val title: String) : TimelineItem
    data class Media(val item: MediaItem) : TimelineItem
}
