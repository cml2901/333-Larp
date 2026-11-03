package eredrin.abilities;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(EredrinAbilities.MODID)
public class EredrinAbilities {
    public static final String MODID = "eredrin_abilities";

    public EredrinAbilities(IEventBus modBus) {
        modBus.addListener(Network::register);
    }
}
