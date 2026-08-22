package org.camelia.studio.kiss.shot.acerola.services.moderation;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class AutoRoleCatchUpService {
    public void catchUp(Guild guild, Role role, Consumer<Result> completion) {
        guild.loadMembers()
                .setTimeout(Duration.ofMinutes(2))
                .onSuccess(members -> assignMissingRoles(guild, role, members, completion))
                .onError(error -> completion.accept(Result.failure(
                        "Impossible de charger les membres : " + error.getMessage())));
    }

    private void assignMissingRoles(
            Guild guild,
            Role role,
            List<Member> members,
            Consumer<Result> completion
    ) {
        List<Member> targets = members.stream()
                .filter(member -> shouldAssign(member, role.getId()))
                .toList();
        if (targets.isEmpty()) {
            completion.accept(new Result(true, 0, 0, 0, "Aucun membre ne nécessite ce rôle."));
            return;
        }

        AtomicInteger remaining = new AtomicInteger(targets.size());
        AtomicInteger assigned = new AtomicInteger();
        AtomicInteger failed = new AtomicInteger();
        AtomicBoolean completed = new AtomicBoolean();
        targets.forEach(member -> guild.addRoleToMember(member, role)
                .reason("Rattrapage manuel du rôle automatique")
                .queue(
                        ignored -> {
                            assigned.incrementAndGet();
                            completeWhenDone(remaining, assigned, failed, completed, completion);
                        },
                        error -> {
                            failed.incrementAndGet();
                            completeWhenDone(remaining, assigned, failed, completed, completion);
                        }));
    }

    static boolean shouldAssign(Member member, String roleId) {
        return !member.getUser().isBot()
                && member.getRoles().stream().noneMatch(role -> role.getId().equals(roleId));
    }

    private void completeWhenDone(
            AtomicInteger remaining,
            AtomicInteger assigned,
            AtomicInteger failed,
            AtomicBoolean completed,
            Consumer<Result> completion
    ) {
        if (remaining.decrementAndGet() != 0 || !completed.compareAndSet(false, true)) {
            return;
        }
        int successes = assigned.get();
        int failures = failed.get();
        completion.accept(new Result(
                failures == 0,
                successes + failures,
                successes,
                failures,
                failures == 0
                        ? "Rattrapage terminé : " + successes + " rôle(s) attribué(s)."
                        : "Rattrapage terminé : " + successes + " attribution(s), " + failures + " échec(s)."));
    }

    public record Result(boolean success, int attempted, int assigned, int failed, String message) {
        static Result failure(String message) {
            return new Result(false, 0, 0, 0, message);
        }
    }
}
