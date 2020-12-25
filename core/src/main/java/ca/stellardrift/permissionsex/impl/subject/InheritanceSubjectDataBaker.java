/*
 * PermissionsEx
 * Copyright (C) zml and PermissionsEx contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.stellardrift.permissionsex.impl.subject;

import ca.stellardrift.permissionsex.PermissionsEngine;
import ca.stellardrift.permissionsex.impl.PermissionsEx;
import ca.stellardrift.permissionsex.context.ContextValue;
import ca.stellardrift.permissionsex.subject.ImmutableSubjectData;
import ca.stellardrift.permissionsex.subject.SubjectRef;
import ca.stellardrift.permissionsex.util.NodeTree;
import ca.stellardrift.permissionsex.impl.util.Util;
import ca.stellardrift.permissionsex.util.glob.GlobParseException;
import ca.stellardrift.permissionsex.util.glob.Globs;
import com.google.common.collect.*;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static java.util.Map.Entry;

/**
 * Handles baking of subject data inheritance tree and context tree into a single data set
 */
class InheritanceSubjectDataBaker implements SubjectDataBaker {
    private static final int CIRCULAR_INHERITANCE_THRESHOLD = 3;
    static final SubjectDataBaker INSTANCE = new InheritanceSubjectDataBaker();

    private InheritanceSubjectDataBaker() {
    }

    private static class BakeState {
        // Accumulators
        private final Map<String, Integer> combinedPermissions = new HashMap<>();
        private final List<Entry<String, String>> parents = new ArrayList<>();
        private final Map<String, String> options = new HashMap<>();
        private int defaultValue;

        // State objects
        private final CalculatedSubjectImpl<?> base;
        private final PermissionsEx<?> pex;
        private final Set<ContextValue<?>> activeContexts;

        private BakeState(CalculatedSubjectImpl<?> base, Set<ContextValue<?>> activeContexts) {
            this.base = base;
            this.activeContexts = activeContexts;
            this.pex = base.getManager();
        }
    }

    private static CompletableFuture<Set<ContextValue<?>>> processContexts(PermissionsEx<?> pex, Set<ContextValue<?>> rawContexts) {
        return pex.getContextInheritance(null).thenApply(inheritance -> {
            // Step one: calculate context inheritance
            Queue<ContextValue<?>> inProgressContexts = new ArrayDeque<>(rawContexts);
            Set<ContextValue<?>> contexts = new HashSet<>();
            @Nullable ContextValue<?> context;
            while ((context = inProgressContexts.poll()) != null) {
                if (contexts.add(context)) {
                    inProgressContexts.addAll(inheritance.parents(context));
                }
            }

            return ImmutableSet.copyOf(contexts);
        });
    }

    @Override
    public CompletableFuture<BakedSubjectData> bake(CalculatedSubjectImpl<?> data, Set<ContextValue<?>> activeContexts) {
        final SubjectRef<?> subject = data.identifier();
        return processContexts(data.getManager(), activeContexts)
                .thenCompose(processedContexts -> {
                    final BakeState state = new BakeState(data, processedContexts);

                    final Multiset<SubjectRef<?>> visitedSubjects = HashMultiset.create();
                    CompletableFuture<Void> ret = visitSubject(state, subject, visitedSubjects, 0);

                    if (state.parents.isEmpty() && state.combinedPermissions.isEmpty() && state.options.isEmpty() && state.defaultValue == 0 && !(subject.type().equals(PermissionsEngine.SUBJECTS_FALLBACK)
                            && subject.identifier().equals(PermissionsEngine.SUBJECTS_FALLBACK.name()))) { // If we have no data, include the fallback subject
                        ret = ret.thenCompose(none -> visitSubject(state, SubjectRef.subject(PermissionsEngine.SUBJECTS_FALLBACK, subject.type().name()), visitedSubjects, 0));
                    }

                    final SubjectRef<String> defIdentifier = data.data().getCache().getDefaultIdentifier();
                    if (!subject.equals(defIdentifier)) {
                        ret = ret.thenCompose(none -> visitSubject(state, defIdentifier, visitedSubjects, 1))
                            .thenCompose(none -> visitSubject(state, SubjectRef.subject(PermissionsEngine.SUBJECTS_DEFAULTS, PermissionsEngine.SUBJECTS_DEFAULTS.name()), visitedSubjects, 2)); // Force in global defaults
                    }
                    return ret.thenApply(none -> state);

                }).thenApply(state -> new BakedSubjectData(NodeTree.of(state.combinedPermissions, state.defaultValue), ImmutableList.copyOf(state.parents), ImmutableMap.copyOf(state.options)));
    }

    private <I> CompletableFuture<Void> visitSubject(BakeState state, SubjectRef<I> subject, Multiset<SubjectRef<?>> visitedSubjects, int inheritanceLevel) {
        if (visitedSubjects.count(subject) > CIRCULAR_INHERITANCE_THRESHOLD) {
            state.pex.logger().warn(Messages.BAKER_ERROR_CIRCULAR_INHERITANCE.tr(state.base.identifier(), subject));
            return Util.emptyFuture();
        }
        visitedSubjects.add(subject);
        SubjectTypeCollectionImpl<I> type = state.pex.subjects(subject.type());
        return type.persistentData().data(subject.identifier(), state.base).thenCombine(type.transientData().data(subject.identifier(), state.base), (persistent, transientData) -> {
            CompletableFuture<Void> ret = Util.emptyFuture();

            for (Set<ContextValue<?>> combo : processContexts(persistent.getActiveContexts(), transientData.getActiveContexts(), state)) {
                if (type.type().transientHasPriority()) {
                    ret = visitSubjectSingle(state, transientData, ret, combo, visitedSubjects, inheritanceLevel);
                    ret = visitSubjectSingle(state, persistent, ret, combo, visitedSubjects, inheritanceLevel);
                } else {
                    ret = visitSubjectSingle(state, persistent, ret, combo, visitedSubjects, inheritanceLevel);
                    ret = visitSubjectSingle(state, transientData, ret, combo, visitedSubjects, inheritanceLevel);
                }
            }
            return ret;
        }).thenCompose(res -> res);
    }

    private List<Set<ContextValue<?>>> processContexts(Set<Set<ContextValue<?>>> possibilities, Set<Set<ContextValue<?>>> transientPossibilities, BakeState state) {
        List<Set<ContextValue<?>>> ret = new ArrayList<>();
        Set<Set<ContextValue<?>>> seen = new HashSet<>(possibilities.size());
        processSingleDataContexts(ret, seen, possibilities, state);
        processSingleDataContexts(ret, seen, transientPossibilities, state);
        ret.sort(Comparator.<Set<ContextValue<?>>>comparingInt(Set::size).reversed());
        return ret;
    }

    /**
     * Add every context set used for a segment in this subject where for a given set,
     * every context matches at least one of the active contexts provided for the query
     *
     * @param accum Accumulator of context sets
     * @param possibilities The possible contexts provided by the subject data
     * @param state The bake state
     */
    private void processSingleDataContexts(List<Set<ContextValue<?>>> accum, Set<Set<ContextValue<?>>> seen, Set<Set<ContextValue<?>>> possibilities, BakeState state) {
        nextSegment: for (Set<ContextValue<?>> segmentContexts : possibilities) {
            if (seen.contains(segmentContexts)) {
                continue;
            }
            seen.add(segmentContexts);

            for (ContextValue<?> value : segmentContexts) {
                boolean matched = false;
                for (ContextValue<?> possibility : state.activeContexts) {
                    if (checkSingleContextMatch(value, possibility, state.pex)) {
                        matched = true;
                        break;
                    }
                }

                if (!matched) {
                    continue nextSegment;
                }
            }
            accum.add(segmentContexts);
        }
    }


    @SuppressWarnings("unchecked")
    private <T> boolean checkSingleContextMatch(ContextValue<T> value, ContextValue<?> other, PermissionsEx<?> pex) {
        return value.key().equals(other.key()) && value.tryResolve(pex)
                && value.definition().matches(value, ((ContextValue<T>) other).getParsedValue(value.definition()));
    }

    private CompletableFuture<Void> visitSubjectSingle(
            BakeState state,
            ImmutableSubjectData data,
            CompletableFuture<Void> initial,
            Set<ContextValue<?>> activeCombo,
            Multiset<SubjectRef<?>> visitedSubjects,
            int inheritanceLevel) {
        initial = initial.thenRun(() -> visitSingle(state, data, activeCombo, inheritanceLevel));
        for (final Map.Entry<String, String> parent : data.getParents(activeCombo)) {
            final SubjectRef<?> ref = state.pex.deserializeSubjectRef(parent);
            initial = initial.thenCompose(none -> visitSubject(state, ref, visitedSubjects, inheritanceLevel + 1));
        }
        return initial;
    }

    private void putPermIfNecessary(final BakeState state, final String perm, final int val) {
        final Integer existing = state.combinedPermissions.get(perm);
        if (existing == null || Math.abs(val) > Math.abs(existing)) {
            state.combinedPermissions.put(perm, val);
        }
    }

    private void visitSingle(
            final BakeState state,
            final ImmutableSubjectData data,
            final Set<ContextValue<?>> specificCombination,
            final int inheritanceLevel) {

        for (Map.Entry<String, Integer> ent : data.getPermissions(specificCombination).entrySet()) {
            String perm = ent.getKey();
            if (ent.getKey().startsWith("#")) { // Prefix to exclude from inheritance
                if (inheritanceLevel > 1) {
                    continue;
                }
                perm = perm.substring(1);
            }

            try {
                for (String matched : Globs.parse(perm)) {
                    putPermIfNecessary(state, matched, ent.getValue());
                }
            } catch (GlobParseException e) { // If the permission is not a valid glob, assume it's a literal
                putPermIfNecessary(state, perm, ent.getValue());
            }
        }

        state.parents.addAll(data.getParents(specificCombination).stream()
                .map(ent -> Maps.immutableEntry(ent.getKey(), ent.getValue())) // TODO: change this?
                .collect(Collectors.toList()));

        for (Map.Entry<String, String> ent : data.getOptions(specificCombination).entrySet()) {
            if (!state.options.containsKey(ent.getKey())) {
                state.options.put(ent.getKey(), ent.getValue());
            }
        }

        if (Math.abs(data.getDefaultValue(specificCombination)) > Math.abs(state.defaultValue)) {
            state.defaultValue = data.getDefaultValue(specificCombination);
        }
    }
}