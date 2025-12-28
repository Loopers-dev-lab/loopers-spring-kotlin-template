package com.loopers.infrastructure.converter

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter
import java.time.YearMonth

@Converter(autoApply = true)
class YearMonthAttributeConverter : AttributeConverter<YearMonth, String> {

    override fun convertToDatabaseColumn(attribute: YearMonth?): String? {
        return attribute?.toString() // "yyyy-MM"
    }

    override fun convertToEntityAttribute(dbData: String?): YearMonth? {
        return dbData?.let { YearMonth.parse(it) }
    }
}
