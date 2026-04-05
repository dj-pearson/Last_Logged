package com.pearsonmedia.lastlogged

import com.pearsonmedia.lastlogged.util.CategoryTemplates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryTemplatesTest {

    @Test
    fun `default categories has 6 entries`() {
        assertEquals(6, CategoryTemplates.defaultCategories.size)
    }

    @Test
    fun `default category names are correct`() {
        val names = CategoryTemplates.defaultCategories.map { it.name }
        assertTrue(names.contains("Home Maintenance"))
        assertTrue(names.contains("Health & Wellness"))
        assertTrue(names.contains("Car Care"))
        assertTrue(names.contains("Personal Care"))
        assertTrue(names.contains("Social & Family"))
        assertTrue(names.contains("Pet Care"))
    }

    @Test
    fun `home maintenance templates exist`() {
        val templates = CategoryTemplates.templatesFor("Home Maintenance")
        assertTrue(templates.isNotEmpty())
        assertTrue(templates.any { it.name == "HVAC Filter" })
    }

    @Test
    fun `health templates exist`() {
        val templates = CategoryTemplates.templatesFor("Health & Wellness")
        assertTrue(templates.isNotEmpty())
        assertTrue(templates.any { it.name == "Dental Checkup" })
    }

    @Test
    fun `car care templates exist`() {
        val templates = CategoryTemplates.templatesFor("Car Care")
        assertTrue(templates.isNotEmpty())
        assertTrue(templates.any { it.name == "Oil Change" })
    }

    @Test
    fun `unknown category returns empty templates`() {
        val templates = CategoryTemplates.templatesFor("Unknown Category")
        assertTrue(templates.isEmpty())
    }

    @Test
    fun `all templates have positive reminder intervals`() {
        CategoryTemplates.defaultCategories.forEach { category ->
            val templates = CategoryTemplates.templatesFor(category.name)
            templates.forEach { template ->
                assertTrue(
                    "Template '${template.name}' has non-positive interval ${template.reminderIntervalDays}",
                    template.reminderIntervalDays > 0
                )
            }
        }
    }

    @Test
    fun `all templates have non-empty names`() {
        CategoryTemplates.defaultCategories.forEach { category ->
            val templates = CategoryTemplates.templatesFor(category.name)
            templates.forEach { template ->
                assertTrue(
                    "Template in ${category.name} has empty name",
                    template.name.isNotBlank()
                )
            }
        }
    }

    @Test
    fun `all templates have non-empty icon names`() {
        CategoryTemplates.defaultCategories.forEach { category ->
            val templates = CategoryTemplates.templatesFor(category.name)
            templates.forEach { template ->
                assertTrue(
                    "Template '${template.name}' has empty icon name",
                    template.iconName.isNotBlank()
                )
            }
        }
    }

    @Test
    fun `all default categories have valid color hex`() {
        val hexPattern = Regex("^#[0-9a-fA-F]{6}$")
        CategoryTemplates.defaultCategories.forEach { category ->
            assertTrue(
                "Category '${category.name}' has invalid color '${category.colorHex}'",
                hexPattern.matches(category.colorHex)
            )
        }
    }
}
