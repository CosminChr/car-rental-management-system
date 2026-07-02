package com.carrental.web.dto

import org.springframework.data.domain.Page

data class PageResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
)

fun <T : Any, R : Any> Page<T>.toPageResponse(transform: (T) -> R): PageResponse<R> =
    PageResponse(
        content = content.map(transform),
        page = number,
        size = size,
        totalElements = totalElements,
        totalPages = totalPages,
    )
