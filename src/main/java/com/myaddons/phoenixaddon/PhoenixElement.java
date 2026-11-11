package com.myaddons.phoenixaddon;

import com.projectkorra.projectkorra.Element;
import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;

public class PhoenixElement {

    public static Element.SubElement PHOENIX;

    public static void register() {

        if (Element.getElement("Phoenix") == null) {
            PHOENIX = new Element.SubElement("Phoenix", Element.FIRE);
        } else {
            PHOENIX = (Element.SubElement) Element.getElement("Phoenix");
        }


        registerPermissions();
    }

    private static void registerPermissions() {
        String masterPermName = "projectkorra.phoenix";


        Permission masterPerm = Bukkit.getPluginManager().getPermission(masterPermName);
        if (masterPerm == null) {
            masterPerm = new Permission(masterPermName, "Доступ ко всем способностям Феникса", PermissionDefault.OP);
            Bukkit.getPluginManager().addPermission(masterPerm);
        }


        String[] abilities = {
                "PhoenixNova",
                "PhoenixBlast",
                "PhoenixWhip",
                "PhoenixBeam",
                "TripleBlitz",
                "Cauterize",
                "IgniteBlood",
                "Rebirth",
                "Amaterasu",
                "PhoenixSmerch",
                "PrometheusChain",
                "PhoenixAscension",
                "PhoenixCrush",
                "PhoenixSwarm",
                "PhoenixFlock",
                "PhoenixSnare",
                "PhoenixBird",
                "PhoenixSlash"
        };


        for (String ability : abilities) {
            String node = "projectkorra.ability." + ability;
            Permission perm = Bukkit.getPluginManager().getPermission(node);

            if (perm == null) {
                perm = new Permission(node, PermissionDefault.OP);
                Bukkit.getPluginManager().addPermission(perm);
            }


            perm.addParent(masterPerm, true);
        }

        for (String combo : new String[]{"PhoenixFlock", "PhoenixSnare"}) {
            String node = "bending.ability." + combo;
            Permission perm = Bukkit.getPluginManager().getPermission(node);

            if (perm == null) {
                perm = new Permission(node, PermissionDefault.OP);
                Bukkit.getPluginManager().addPermission(perm);
            }

            perm.addParent(masterPerm, true);
        }
    }
}