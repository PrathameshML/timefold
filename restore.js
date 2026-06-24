const fs = require('fs');
const content = `    // ============ ShiftConstraintsV2 — Dynamic Constraint Provider ============
    public static class ShiftConstraintsV2 implements ConstraintProvider {

        private static class Context {
            Map<String, Integer> maxWorkersPerRole = new HashMap<>();
            Map<String, List<Integer>> requiredRatingsPerRole = new HashMap<>();
            Map<String, List<String>> requiredSkillsPerRole = new HashMap<>();
            List<ConstraintConfig> activeConstraintConfigs = new ArrayList<>();
            double averageWage = 1.0;
        }

        private static final ThreadLocal<Context> threadContext = ThreadLocal.withInitial(Context::new);

        public ShiftConstraintsV2() {}

        public static void setConfiguration(
                List<Scheduler.ShiftSchedule.RoleLimit> roleLimits,
                List<Scheduler.ShiftSchedule.RatingRequirement> ratingRequirements,
                Map<String, List<String>> skillsMap,
                List<ConstraintConfig> configs,
                double avgWage) {

            Context ctx = threadContext.get();
            ctx.maxWorkersPerRole.clear();
            ctx.requiredRatingsPerRole.clear();
            ctx.requiredSkillsPerRole = skillsMap != null ? new HashMap<>(skillsMap) : new HashMap<>();
            ctx.activeConstraintConfigs = configs != null ? new ArrayList<>(configs) : new ArrayList<>();
            ctx.averageWage = avgWage > 0 ? avgWage : 1.0;

            for (Scheduler.ShiftSchedule.RoleLimit l : roleLimits) {
                ctx.maxWorkersPerRole.put(l.getRoleName(), l.getMaxWorkers());
            }
            for (Scheduler.ShiftSchedule.RatingRequirement r : ratingRequirements) {
                ctx.requiredRatingsPerRole.put(r.getRoleName(), r.getAllowedRatings());
            }

            System.out.println("✅ ShiftConstraintsV2 configured on thread " + Thread.currentThread().getName() + ": " +
                    ctx.maxWorkersPerRole.size() + " role limits, " +
                    ctx.requiredSkillsPerRole.size() + " skill requirements, " +
                    ctx.activeConstraintConfigs.size() + " constraint configs");
        }

        public static void clearConfiguration() {
            threadContext.remove();
        }

        private HardMediumSoftLongScore resolveScore(String constraintName, String severity) {
            return switch (severity.toUpperCase()) {
                case "HARD" -> HardMediumSoftLongScore.ONE_HARD;
                case "MEDIUM" -> HardMediumSoftLongScore.ONE_MEDIUM;
                case "SOFT" -> HardMediumSoftLongScore.ONE_SOFT;
                default -> HardMediumSoftLongScore.ONE_HARD;
            };
        }

        private boolean isConstraintActive(String name, String severity, Object dummyFact) {
            if (dummyFact == null) return false;
            Context ctx = threadContext.get();
            for (ConstraintConfig cc : ctx.activeConstraintConfigs) {
                if (cc.isEnabled() && cc.getConstraintName().equals(name) && cc.getSeverity().equalsIgnoreCase(severity)) {
                    return true;
                }
            }
            return false;
        }

        private double getConstraintParameter(String name, double defaultValue, Object dummyFact) {
            if (dummyFact == null) return defaultValue;
            Context ctx = threadContext.get();
            for (ConstraintConfig cc : ctx.activeConstraintConfigs) {
                if (cc.isEnabled() && cc.getConstraintName().equals(name) && cc.getParameterValue() != null) {
                    return cc.getParameterValue();
                }
            }
            return defaultValue;
        }

        // Constraint #1: Skill Match (percentage-based)
        private Constraint buildSkillMatch(ConstraintFactory factory, String severity) {
            return factory.forEach(Scheduler.EmployeeAssignment.class)
                    .filter(a -> a.getShift() != null)
                    .filter(a -> isConstraintActive("skillMatch", severity, a))
                    .filter(a -> {
                        double matchPercentage = getConstraintParameter("skillMatch", 100.0, a);
                        List<String> required = threadContext.get().requiredSkillsPerRole.getOrDefault(a.getPosition(), List.of());
                        if (required.isEmpty()) return false;
                        long matchedCount = required.size() - a.getMissingSkillCount();
                        double actualPct = (matchedCount * 100.0) / required.size();
                        return actualPct < matchPercentage;
                    })
                    .penalizeLong(resolveScore("skillMatch", severity),
                            a -> (long) a.getMissingSkillCount())
                    .asConstraint("skillMatch_" + severity);
        }

        // Constraint #2: No Overlapping Shifts
        private Constraint buildNoOverlappingShifts(ConstraintFactory factory, String severity) {
            return factory.forEachUniquePair(Scheduler.EmployeeAssignment.class,
                            Joiners.equal(Scheduler.EmployeeAssignment::getEmployeeId),
                            Joiners.equal(Scheduler.EmployeeAssignment::getDate))
                    .filter((a1, a2) -> a1.getShift() != null && a2.getShift() != null)
                    .filter((a1, a2) -> isConstraintActive("noOverlappingShifts", severity, a1))
                    .penalize(resolveScore("noOverlappingShifts", severity))
                    .asConstraint("noOverlappingShifts_" + severity);
        }

        // Constraint #3: Unavailable Timeslots / Rating Mismatch
        private Constraint buildUnavailableTimeslotOrRatingMismatch(ConstraintFactory factory, String severity) {
            return factory.forEach(Scheduler.EmployeeAssignment.class)
                    .filter(a -> a.getShift() != null)
                    .filter(a -> isConstraintActive("unavailableTimeslot", severity, a))
                    .filter(a -> !a.isAvailable() || a.isRatingMismatch())
                    .penalize(resolveScore("unavailableTimeslot", severity))
                    .asConstraint("unavailableTimeslot_ratingMismatch_" + severity);
        }

        // Constraint #4: Every Shift Planned (Required Roles Fulfilled)
        private Constraint buildEveryShiftPlanned(ConstraintFactory factory, String severity) {
            return factory.forEachIncludingNullVars(Scheduler.EmployeeAssignment.class)
                    .filter(a -> a.getRequestedShift() != null)
                    .groupBy(
                            Scheduler.EmployeeAssignment::getDate,
                            Scheduler.EmployeeAssignment::getPosition,
                            ConstraintCollectors.sumLong(a -> a.getShift() != null ? 1L : 0L))
                    .join(Scheduler.ShiftSchedule.RoleLimit.class,
                            Joiners.equal((date, position, count) -> position,
                                    Scheduler.ShiftSchedule.RoleLimit::getRoleName))
                    .filter((date, position, count, roleLimit) -> isConstraintActive("everyShiftPlanned", severity, roleLimit))
                    .filter((date, position, count, roleLimit) -> count < roleLimit.getMaxWorkers())
                    .penalizeLong(resolveScore("everyShiftPlanned", severity),
                            (date, position, count, roleLimit) -> (long) (roleLimit.getMaxWorkers() - count))
                    .asConstraint("everyShiftPlanned_" + severity);
        }

        // Constraint #5: Wage Optimization (Minimize Costs)
        private Constraint buildWageOptimization(ConstraintFactory factory, String severity) {
            return factory.forEach(Scheduler.EmployeeAssignment.class)
                    .filter(a -> a.getShift() != null)
                    .filter(a -> isConstraintActive("wageOptimization", severity, a))
                    .penalizeBigDecimal(resolveScore("wageOptimization", severity),
                            a -> {
                                double normalizedCost = a.getHourlyWage() / threadContext.get().averageWage;
                                return java.math.BigDecimal.valueOf(normalizedCost * a.getShiftDurationHours());
                            })
                    .asConstraint("wageOptimization_" + severity);
        }

        // Constraint #6: Max Daily Hours
        private Constraint buildMaxDailyHours(ConstraintFactory factory, String severity) {
            return factory.forEach(Scheduler.EmployeeAssignment.class)
                    .filter(a -> a.getShift() != null)
                    .filter(a -> isConstraintActive("maxDailyHours", severity, a))
                    .filter(a -> a.getShiftDurationHours() > getConstraintParameter("maxDailyHours", 8.0, a))
                    .penalizeLong(resolveScore("maxDailyHours", severity),
                            a -> {
                                double maxHours = getConstraintParameter("maxDailyHours", 8.0, a);
                                return (long) Math.ceil(a.getShiftDurationHours() - maxHours);
                            })
                    .asConstraint("maxDailyHours_" + severity);
        }

        // Constraint #7: Max Weekly Hours
        private Constraint buildMaxWeeklyHours(ConstraintFactory factory, String severity) {
            return factory.forEach(Scheduler.EmployeeAssignment.class)
                    .filter(a -> a.getShift() != null)
                    .filter(a -> isConstraintActive("maxWeeklyHours", severity, a))
                    .groupBy(Scheduler.EmployeeAssignment::getEmployeeId,
                            a -> a.getDate().get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR),
                            ConstraintCollectors.sumLong(a -> (long) (a.getShiftDurationHours() * 60)))
                    .filter((empId, weekNum, totalMinutes) -> totalMinutes > getConstraintParameter("maxWeeklyHours", 40.0, empId) * 60)
                    .penalizeLong(resolveScore("maxWeeklyHours", severity),
                            (empId, weekNum, totalMinutes) -> {
                                double maxWeeklyHours = getConstraintParameter("maxWeeklyHours", 40.0, empId);
                                long extraMinutes = totalMinutes - (long)(maxWeeklyHours * 60);
                                return extraMinutes / 60;
                            })
                    .asConstraint("maxWeeklyHours_" + severity);
        }

        // Constraint #8: Overtime Threshold
        private Constraint buildOvertimeThreshold(ConstraintFactory factory, String severity) {
            return factory.forEach(Scheduler.EmployeeAssignment.class)
                    .filter(a -> a.getShift() != null)
                    .filter(a -> isConstraintActive("overtimeThreshold", severity, a))
                    .filter(a -> a.getShiftDurationHours() > getConstraintParameter("overtimeThreshold", 8.0, a))
                    .penalizeLong(resolveScore("overtimeThreshold", severity),
                            a -> {
                                double threshold = getConstraintParameter("overtimeThreshold", 8.0, a);
                                return (long) Math.ceil(a.getShiftDurationHours() - threshold);
                            })
                    .asConstraint("overtimeThreshold_" + severity);
        }

        // Constraint #9: Break After Hours
        private Constraint buildBreakAfterHours(ConstraintFactory factory, String severity) {
            return factory.forEach(Scheduler.EmployeeAssignment.class)
                    .filter(a -> a.getShift() != null)
                    .filter(a -> isConstraintActive("breakAfterHours", severity, a))
                    .filter(a -> {
                        double maxHours = getConstraintParameter("breakAfterHours", 4.0, a);
                        return a.getShiftDurationHours() > maxHours && a.getAssignedBreaks() == 0;
                    })
                    .penalizeLong(resolveScore("breakAfterHours", severity),
                            a -> {
                                double maxHours = getConstraintParameter("breakAfterHours", 4.0, a);
                                return (long) Math.ceil(a.getShiftDurationHours() - maxHours);
                            })
                    .asConstraint("breakAfterHours_" + severity);
        }

        // Constraint #10: Consecutive Shifts
        private Constraint buildConsecutiveShifts(ConstraintFactory factory, String severity) {
            return factory.forEach(Scheduler.EmployeeAssignment.class)
                    .filter(a -> a.getShift() != null)
                    .filter(a -> isConstraintActive("consecutiveShifts", severity, a))
                    .groupBy(Scheduler.EmployeeAssignment::getEmployeeId, ConstraintCollectors.toList())
                    .penalizeLong(resolveScore("consecutiveShifts", severity),
                            (employeeId, list) -> {
                                if (list.size() < 2) return 0L;
                                List<Scheduler.EmployeeAssignment> sortedList = new ArrayList<>(list);
                                sortedList.sort(Comparator.comparing(Scheduler.EmployeeAssignment::getLocalDateObj));
                                long totalGaps = 0;
                                for (int i = 0; i < sortedList.size() - 1; i++) {
                                    long daysBetween = java.time.temporal.ChronoUnit.DAYS.between(sortedList.get(i).getLocalDateObj(), sortedList.get(i + 1).getLocalDateObj());
                                    if (daysBetween > 1) {
                                        totalGaps += (daysBetween - 1);
                                    }
                                }
                                return totalGaps;
                            })
                    .asConstraint("consecutiveShifts_" + severity);
        }

        // Constraint #11: Permanent Priority
        private Constraint buildPermanentPriority(ConstraintFactory factory, String severity) {
            return factory.forEach(Scheduler.EmployeeAssignment.class)
                    .filter(a -> a.getShift() == null) // Unassigned employee
                    .filter(a -> "Permanent".equalsIgnoreCase(a.getEmployeeCategory()))
                    .filter(a -> isConstraintActive("permanentPriority", severity, a))
                    .penalize(resolveScore("permanentPriority", severity))
                    .asConstraint("permanentPriority_" + severity);
        }

        // Constraint #12: Maximize Rating
        private Constraint buildMaximizeRating(ConstraintFactory factory, String severity) {
            return factory.forEach(Scheduler.EmployeeAssignment.class)
                    .filter(a -> a.getShift() != null)
                    .filter(a -> isConstraintActive("maximizeRating", severity, a))
                    .rewardLong(resolveScore("maximizeRating", severity),
                            a -> {
                                long multiplier = (long) getConstraintParameter("maximizeRating", 100.0, a);
                                return a.getPerformanceRating() * multiplier;
                            })
                    .asConstraint("maximizeRating_" + severity);
        }

        // Also add max workers per role and rating mismatch as always-on constraints
        @Override
        public Constraint[] defineConstraints(ConstraintFactory factory) {
            List<Constraint> constraints = new ArrayList<>();

            // Always-on: max workers per role per shift
            constraints.add(
                factory.forEach(Scheduler.EmployeeAssignment.class)
                    .filter(a -> a.getShift() != null && a.getRequestedShift() != null)
                    .groupBy(
                            Scheduler.EmployeeAssignment::getDate,
                            Scheduler.EmployeeAssignment::getPosition,
                            ConstraintCollectors.count())
                    .join(Scheduler.ShiftSchedule.RoleLimit.class,
                            Joiners.equal((date, position, count) -> position,
                                    Scheduler.ShiftSchedule.RoleLimit::getRoleName))
                    .filter((date, position, count, roleLimit) -> count > roleLimit.getMaxWorkers())
                    .penalizeLong(HardMediumSoftLongScore.ONE_HARD,
                            (date, position, count, roleLimit) -> (long) (count - roleLimit.getMaxWorkers()) * 10000L)
                    .asConstraint("maxWorkersPerRolePerShift")
            );

            // Dynamic constraints for all severities
            String[] severities = {"HARD", "MEDIUM", "SOFT"};
            for (String sev : severities) {
                constraints.add(buildSkillMatch(factory, sev));
                constraints.add(buildNoOverlappingShifts(factory, sev));
                constraints.add(buildUnavailableTimeslotOrRatingMismatch(factory, sev));
                constraints.add(buildEveryShiftPlanned(factory, sev));
                constraints.add(buildWageOptimization(factory, sev));
                constraints.add(buildMaxDailyHours(factory, sev));
                constraints.add(buildMaxWeeklyHours(factory, sev));
                constraints.add(buildOvertimeThreshold(factory, sev));
                constraints.add(buildBreakAfterHours(factory, sev));
                constraints.add(buildConsecutiveShifts(factory, sev));
                constraints.add(buildPermanentPriority(factory, sev));
                constraints.add(buildMaximizeRating(factory, sev));
            }

            System.out.println("📋 ShiftConstraintsV2: Built " + constraints.size() + " total constraints (runtime evaluated)");
            return constraints.toArray(new Constraint[0]);
        }
`;

let file = fs.readFileSync('C:/Users/user/Downloads/timefold/timefold/src/main/java/com/scheduler/ShiftApp.java', 'utf8');

// Find the start of ShiftConstraintsV2 and the end of defineConstraints
const startIndex = file.indexOf('    // ============ ShiftConstraintsV2 — Dynamic Constraint Provider ============');
const endIndexStr = '        private static double calculateDurationHoursV2(String startStr, String endStr) {';
const endIndex = file.indexOf(endIndexStr);

if (startIndex > -1 && endIndex > -1) {
    file = file.substring(0, startIndex) + content + '\n' + file.substring(endIndex);
    
    // Now fix assignShiftsV2 usages
    // 1. Restore localConstraintConfigs
    file = file.replace(/constraintConfigs = mysqlService\.loadAllConstraintConfigs\(\);/g, 'List<ConstraintConfig> localConstraintConfigs = mysqlService.loadAllConstraintConfigs();');
    file = file.replace(/constraintConfigs\.isEmpty\(\)/g, 'localConstraintConfigs.isEmpty()');
    file = file.replace(/constraintConfigs = getDefaultConstraintConfigs\(\);/g, 'localConstraintConfigs = getDefaultConstraintConfigs();');
    file = file.replace(/constraintConfigs\.forEach/g, 'localConstraintConfigs.forEach');
    file = file.replace(/constraintConfigs\.stream\(\)/g, 'localConstraintConfigs.stream()');
    file = file.replace(/constraintConfigs, averageWage\);/g, 'localConstraintConfigs, averageWage);');
    file = file.replace(/for \(ConstraintConfig cc : constraintConfigs\) \{/g, 'for (ConstraintConfig cc : localConstraintConfigs) {');

    // 2. Add clearConfiguration to finally block
    const returnOkStr = 'return Response.ok(response).build();';
    const tryReplace = `                return Response.ok(response).build();
            } finally {
                ShiftConstraintsV2.clearConfiguration();
            }`;
    
    if (file.indexOf('ShiftConstraintsV2.clearConfiguration();') === -1) {
        file = file.replace(returnOkStr, tryReplace);
    }

    fs.writeFileSync('C:/Users/user/Downloads/timefold/timefold/src/main/java/com/scheduler/ShiftApp.java', file);
    console.log('Successfully reverted code.');
} else {
    console.log('Failed to find boundaries.', startIndex, endIndex);
}
