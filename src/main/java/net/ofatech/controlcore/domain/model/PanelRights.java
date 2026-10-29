package net.ofatech.controlcore.domain.model;

public record PanelRights(
        boolean panelEnabled,
        boolean admin,
        boolean adminPanel,
        boolean history,
        boolean rollback,
        boolean snapshots,
        boolean inputProbe,
        boolean players,
        boolean diagnostics,
        boolean automation) {}
