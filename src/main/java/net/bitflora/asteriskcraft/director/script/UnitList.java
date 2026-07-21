package net.bitflora.asteriskcraft.director.script;

import java.util.List;

/**
 * A comma-delimited list of {@link UnitReq}s — the {@code [UnitList]} parameter of the
 * {@code Defence}/{@code Wave} commands, e.g. {@code 3-5 Zergling, 1 Hydralisk}.
 */
public record UnitList(List<UnitReq> reqs) {
    public UnitList {
        reqs = List.copyOf(reqs);
    }
}
