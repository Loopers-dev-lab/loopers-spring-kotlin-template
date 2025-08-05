package com.loopers.support.sort

import org.springframework.data.domain.Sort

interface SortType {
    fun toSort(): Sort
}
