package net.ofatech.controlcore.domain.ports.config;

import java.util.List;
import net.ofatech.controlcore.domain.model.keys.CustomKeyBind;

/** Key actions created by admins. */
public interface CustomKeyBindStore {

    /** @return every saved bind, sorted by name */
    List<CustomKeyBind> all();

    /** Adds or replaces the bind with the same name and saves at once. */
    void put(CustomKeyBind bind);

    /** Removes the bind with that name and saves at once; does nothing if there is none. */
    void remove(String name);
}
