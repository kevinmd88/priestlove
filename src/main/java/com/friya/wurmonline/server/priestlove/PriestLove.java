/*
 * Decompiled with CFR 0_123.
 * 
 * Could not load the following classes:
 *  com.wurmonline.server.Players
 *  com.wurmonline.server.creatures.Communicator
 *  com.wurmonline.server.creatures.Creature
 *  com.wurmonline.server.items.Item
 *  com.wurmonline.server.items.ItemSpellEffects
 *  com.wurmonline.server.players.Player
 *  com.wurmonline.server.spells.SpellEffect
 *  javassist.CannotCompileException
 *  javassist.ClassPool
 *  javassist.CtClass
 *  javassist.CtMethod
 *  javassist.CtPrimitiveType
 *  javassist.NotFoundException
 *  javassist.bytecode.Descriptor
 *  javassist.expr.ExprEditor
 *  javassist.expr.MethodCall
 *  org.gotti.wurmunlimited.modloader.classhooks.HookManager
 *  org.gotti.wurmunlimited.modloader.classhooks.InvocationHandlerFactory
 *  org.gotti.wurmunlimited.modloader.interfaces.Configurable
 *  org.gotti.wurmunlimited.modloader.interfaces.Initable
 *  org.gotti.wurmunlimited.modloader.interfaces.PreInitable
 *  org.gotti.wurmunlimited.modloader.interfaces.ServerStartedListener
 *  org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod
 *  org.gotti.wurmunlimited.modsupport.ModSupportDb
 */
package com.friya.wurmonline.server.priestlove;

import com.wurmonline.server.creatures.Communicator;
import com.wurmonline.server.creatures.Creature;
import com.wurmonline.server.items.Item;
import com.wurmonline.server.items.ItemSpellEffects;
import com.wurmonline.server.players.Player;
import com.wurmonline.server.players.PlayerInfo;
import com.wurmonline.server.players.PlayerInfoFactory;
import com.wurmonline.server.spells.SpellEffect;
import javassist.*;
import javassist.bytecode.Descriptor;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.*;
import org.gotti.wurmunlimited.modsupport.ModSupportDb;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PriestLove
implements WurmServerMod, Initable, PreInitable, Configurable, ServerStartedListener {
    private static Logger logger = Logger.getLogger(PriestLove.class.getName());
    private static PriestLove instance = null;
    private static final String TABLE_SPELLCASTERS = "FriyaSpellCasters";
    protected static boolean useVanillaMessage = true;
    protected static boolean obscureNames = true;

    public static PriestLove getInstance() {
        return instance;
    }

    public void configure(Properties properties) {
        useVanillaMessage = Boolean.parseBoolean(properties.getProperty("useVanillaMessage", Boolean.toString(useVanillaMessage)));
        obscureNames = Boolean.parseBoolean(properties.getProperty("obscureNames", Boolean.toString(obscureNames)));
    }

    public void onServerStarted() {
        try {
            Connection con = ModSupportDb.getModSupportDb();
            if (!ModSupportDb.hasTable(con, "FriyaSpellCasters")) {
                this.unsafeDBexecute("CREATE TABLE FriyaSpellCasters (itemid BIGINT NOT NULL, spelltype INT NOT NULL, casterid BIGINT NOT NULL)");
                logger.info("Created table FriyaSpellCasters");
                this.unsafeDBexecute("CREATE UNIQUE INDEX FriyaSpellCasterLookup ON FriyaSpellCasters(itemid, spelltype);");
                logger.info("Created index FriyaSpellCasterLookup");
            }
        }
        catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to set up PriestLove :(\n" + e.toString());
        }
    }

    public void preInit() {
    }

    public void init() {
        instance = this;
        final String[] itemSpells = new String[]{"Courier", "Nimbleness", "LurkerDeep", "Opulence", "CircleOfCunning", "MindStealer", "Bloodthirst", "SharedPain", "DarkMessenger", "LurkerWoods", "RottingTouch", "Frostbrand", "Nolocate", "LurkerDark", "WindOfAges", "Venom", "FlamingAura", "WebArmour", "BlessingDark", "LifeTransfer"};
        try {
            ClassPool cp = HookManager.getInstance().getClassPool();
            CtClass c = cp.get("com.wurmonline.server.items.Item");
            c.getDeclaredMethods("sendEnchantmentStrings")[0].instrument(new ExprEditor(){

                public void edit(MethodCall m) throws CannotCompileException {
                    if (m.getMethodName().equals("getSpellEffects")) {
                        logger.info("Replaced enchantment information output.");
                        m.replace(PriestLove.class.getName()+".getInstance().sendEnchantmentStrings(comm, $0);" +
                                "$_ = null;");
                    }
                }
            });
            int i = 0;
            while (i < itemSpells.length) {
                final int j = i;
                c = cp.get("com.wurmonline.server.spells." + itemSpells[i]);
                c.getDeclaredMethods("doEffect")[0].instrument(new ExprEditor(){

                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getMethodName().equals("addSpellEffect")) {
                            logger.info("Improving (1) " + itemSpells[j]);
                            m.replace("$_ = $proceed($$);" +
                                    PriestLove.class.getName()+".getInstance().addSpellEffectCaster($0, eff, performer); ");
                        } else if (m.getMethodName().equals("improvePower")) {
                            logger.info("Improving (2) " + itemSpells[j]);
                            m.replace("$_ = $proceed($$);" +
                                    PriestLove.class.getName()+".getInstance().replaceSpellEffectCaster($0, eff, performer); ");
                        }
                    }
                });
                ++i;
            }
            String descriptor = Descriptor.ofMethod(cp.get("com.wurmonline.server.spells.SpellEffect"), new CtClass[]{CtPrimitiveType.byteType});
            HookManager.getInstance().registerHook("com.wurmonline.server.items.ItemSpellEffects", "removeSpellEffect", descriptor, () -> (proxy, method, args) -> {
                Object ret = method.invoke(proxy, args);
                PriestLove.getInstance().removeSpellEffectCaster((SpellEffect)ret);
                return ret;
            });
            descriptor = Descriptor.ofMethod(CtPrimitiveType.voidType, new CtClass[0]);
            HookManager.getInstance().registerHook("com.wurmonline.server.items.ItemSpellEffects", "destroy", descriptor, () -> (proxy, method, args) -> {
                PriestLove.getInstance().removeAllSpellEffectCasters((ItemSpellEffects)proxy);
                Object ret = method.invoke(proxy, args);
                return ret;
            });
        }
        catch (CannotCompileException | NotFoundException e) {
            logger.log(Level.SEVERE, e.toString());
        }
    }

    public void addSpellEffectCaster(Object spell, SpellEffect eff, Creature caster) {
        if (caster instanceof Player) {
            this.unsafeDBexecute("INSERT INTO FriyaSpellCasters VALUES(" + eff.owner + ", " + eff.type + ", " + caster.getWurmId() + ")");
        }
    }

    public void replaceSpellEffectCaster(Object spell, SpellEffect eff, Creature caster) {
        if (caster instanceof Player) {
            this.removeSpellEffectCaster(eff);
            this.addSpellEffectCaster(spell, eff, caster);
        }
    }

    public void removeSpellEffectCaster(SpellEffect eff) {
        this.unsafeDBexecute("DELETE FROM FriyaSpellCasters WHERE itemid = " + eff.owner + " AND spelltype = " + eff.type);
    }

    public void removeAllSpellEffectCasters(ItemSpellEffects spellEffects) {
        SpellEffect[] effs = spellEffects.getEffects();
        if (effs.length > 0 && effs[0].owner > 0) {
            this.unsafeDBexecute("DELETE FROM FriyaSpellCasters WHERE itemid = " + effs[0].owner);
        }
    }

    public void sendEnchantmentStrings(Communicator comm, Item item) {
        String caster;
        HashMap<Byte, Long> castsLookup = new HashMap<>();
        ItemSpellEffects eff = item.getSpellEffects();
        if (eff != null) {
            SpellEffect[] speffs = eff.getEffects();
            if (speffs.length > 0) {
                String sql = "SELECT spelltype, casterid FROM FriyaSpellCasters WHERE itemid = ?";
                try {
                    Connection con = ModSupportDb.getModSupportDb();
                    PreparedStatement ps = con.prepareStatement(sql);
                    ps.setLong(1, speffs[0].owner);
                    ResultSet rs = ps.executeQuery();
                    while (rs.next()) {
                        castsLookup.put(rs.getByte(1), rs.getLong(2));
                    }
                }
                catch (SQLException e) {
                    logger.info("Non-critical error, failed to exeute " + sql + "\n" + e.toString());
                }
            }
            //Player p;
            int x = 0;
            while (x < speffs.length) {
                if (speffs[x].isSmeared()) {
                    comm.sendNormalServerMessage("It has been imbued with special abilities, and it " + speffs[x].getLongDesc() + " [" + (int)speffs[x].power + "]");
                } else if ((long)speffs[x].type < -10) {
                    comm.sendNormalServerMessage("A single " + speffs[x].getName() + " has been attached to it, so it " + speffs[x].getLongDesc());
                } else {
                    if(castsLookup.containsKey(speffs[x].type)){
                        PlayerInfo info = PlayerInfoFactory.getPlayerInfoWithWurmId(castsLookup.get(speffs[x].type));
                        //p = Players.getInstance().getPlayerOrNull(castsLookup.get(speffs[x].type));
                        if(info != null){
                            if(obscureNames) {
                                caster = this.getSignature(info.getName(), (int) speffs[x].power);
                            }else{
                                caster = info.getName();
                            }
                        }else{
                            caster = "a priest";
                        }
                    }else{
                        caster = "a priest";
                    }
                    //caster = !castsLookup.containsKey(speffs[x].type) || (p = Players.getInstance().getPlayerOrNull(castsLookup.get(speffs[x].type))) == null ? "a priest" : this.getSignature(p.getName(), (int)speffs[x].power);
                    if(useVanillaMessage){
                        this.tell(comm, speffs[x].getName() + " has been cast on it, so it " + speffs[x].getLongDesc() + " [" + (int)speffs[x].power + "] Casted by "+caster+".");
                    }else {
                        //this.tell(comm, String.valueOf(speffs[x].getName()) + " with a power of " + (int) speffs[x].power + " has been cast on it by " + caster + ". This " + speffs[x].getLongDesc());
                        this.tell(comm, speffs[x].getName() + " has been cast on it by "+caster+".", so it " + speffs[x].getLongDesc() + " [" + (int)speffs[x].power + "]");
                    }
                }
                ++x;
            }
        }
    }

    private void tell(Communicator c, String msg) {
        c.sendNormalServerMessage(msg);
    }

    private String getSignature(String name, int ql) {
        if (name != null && name.length() > 0) {
            String toReturn = name;
            if (ql < 20) {
                return "..?.";
            }
            if (ql < 90) {
                toReturn = Item.obscureWord(name, ql);
            }
            return toReturn;
        }
        return name;
    }

    private void unsafeDBexecute(String sql) {
        try {
            Connection con = ModSupportDb.getModSupportDb();
            PreparedStatement ps = con.prepareStatement(sql);
            ps.execute();
            ps.close();
        }
        catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to execute " + sql + "\n" + e.toString());
        }
    }

}

