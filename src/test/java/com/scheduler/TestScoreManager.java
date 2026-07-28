package com.scheduler;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import com.scheduler.model.ShiftSchedule;

public class TestScoreManager {
    public static void main(String[] args) {
        SolverFactory<ShiftSchedule> solverFactory = SolverFactory.createFromXmlResource("solverConfig.xml");
        SolutionManager<ShiftSchedule, HardMediumSoftLongScore> sm = SolutionManager.create(solverFactory);
        System.out.println(sm.explain(new ShiftSchedule()).getSummary());
    }
}
