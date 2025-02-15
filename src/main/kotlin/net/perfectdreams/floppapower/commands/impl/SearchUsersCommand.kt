package net.perfectdreams.floppapower.commands.impl

import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.SlashCommandEvent
import net.dv8tion.jda.api.sharding.ShardManager
import net.perfectdreams.floppapower.commands.AbstractSlashCommand
import net.perfectdreams.floppapower.utils.Constants
import net.perfectdreams.floppapower.utils.InfoGenerationUtils
import java.time.OffsetDateTime
import java.time.ZoneId


class SearchUsersCommand(private val shardManager: ShardManager) : AbstractSlashCommand("searchusers") {
    companion object {
        // 1000 users is around 493 KB
        // so let's to 15_000 users!
        const val MAX_USERS_PER_LIST = 15_000
    }

    override fun execute(event: SlashCommandEvent) {
        event.deferReply().queue()
        val hook = event.hook // This is a special webhook that allows you to send messages without having permissions in the channel and also allows ephemeral messages

        val regexPatternAsString = event.getOption("pattern")?.asString!! // This is the pattern that the user wants to search for
        val regex = Regex(regexPatternAsString, RegexOption.IGNORE_CASE)

        val sortBy = (event.getOption("sort_by")?.asString  ?: "creation_date")// This is the sort that the user wants to use

        val list = (event.getOption("list")?.asString?.toBoolean() ?: false) // Will create only the list with the users name and id

        val creationTimeDayFilter = (event.getOption("creation_time_filter")?.asString?.toLong() ?: "36500".toLong())

        val now = OffsetDateTime.now(ZoneId.of("America/Sao_Paulo"))
            .minusDays(creationTimeDayFilter)

        val matchedUsers = mutableListOf<User>()
        var tooManyUsers = false

        shardManager.userCache.forEach {
            if (matchedUsers.size >= MAX_USERS_PER_LIST) {
                tooManyUsers = true
                return@forEach
            }

            if (it.name.matches(regex)) {
                matchedUsers.add(it)
            }
        }

        val filteredMatchedUsers = matchedUsers.filter { it.timeCreated.isAfter(now) }

        val builder = StringBuilder("Users (${filteredMatchedUsers.size}):")
        builder.append("\n")

        if (list) {
            val filteredAndSortedUsers = if (sortBy == "creation_date") {
                filteredMatchedUsers.sortedByDescending { it.timeCreated }
            } else if (sortBy == "alphabetically") { // alphabetically, needs to be exaustive
                filteredMatchedUsers.sortedBy { it.name }
            } else {
                filteredMatchedUsers.sortedByDescending { it.mutualGuilds.size }
            }

            filteredAndSortedUsers.forEach {
                builder.append("${it.name}#${it.discriminator} (${it.idLong}) - [${Constants.DATE_FORMATTER.format(it.timeCreated)}]")
                builder.append("\n")
            }
        } else {
            val filteredAndSortedUsers = if (sortBy == "creation_date") {
                filteredMatchedUsers.sortedByDescending { it.timeCreated }
            } else if (sortBy == "alphabetically") { // alphabetically, needs to be exaustive
                filteredMatchedUsers.sortedBy { it.name }
            } else {
                filteredMatchedUsers.sortedByDescending { it.mutualGuilds.size }
            }

            filteredAndSortedUsers.forEach {
                InfoGenerationUtils.generateUserInfoLines(shardManager, it).first.forEach {
                    builder.append(it)
                    builder.append("\n")
                }
                builder.append("\n")
            }
        }

        hook
            .editOriginal(if (tooManyUsers) "Tem tantos usuários que eu limitei a $MAX_USERS_PER_LIST usuários! <a:floppaTeeth:849638419885195324>" else "<a:SCfloppaEARflop2:750859905858142258>")
            .addFile(builder.toString().take(8_000_000).toByteArray(Charsets.UTF_8), "users.txt")
            .queue()
    }
}