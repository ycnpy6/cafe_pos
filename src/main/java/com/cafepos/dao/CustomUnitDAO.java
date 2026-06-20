package com.cafepos.dao;

import com.cafepos.db.DatabaseManager;
import com.cafepos.model.CustomUnit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * CRUD for the {@code custom_units} table. Used by {@link com.cafepos.model.UnitRegistry}
 * to overlay user-defined units on top of the built-in {@link com.cafepos.model.UnitType}
 * enum.
 */
public class CustomUnitDAO {

    public List<CustomUnit> findAll() throws Exception {
        List<CustomUnit> out = new ArrayList<>();
        String sql = "SELECT id, display_unit, base_unit, factor_to_base, family, label, active "
                + "FROM custom_units ORDER BY display_unit";
        try (Connection conn = DatabaseManager.openConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(mapRow(rs));
            }
        }
        return out;
    }

    public List<CustomUnit> findAllActive() throws Exception {
        List<CustomUnit> out = new ArrayList<>();
        String sql = "SELECT id, display_unit, base_unit, factor_to_base, family, label, active "
                + "FROM custom_units WHERE active = 1 ORDER BY display_unit";
        try (Connection conn = DatabaseManager.openConnection();
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                out.add(mapRow(rs));
            }
        }
        return out;
    }

    public int insert(CustomUnit unit) throws Exception {
        String sql = "INSERT INTO custom_units (display_unit, base_unit, factor_to_base, family, label, active) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, unit.getDisplayUnit());
            ps.setString(2, unit.getBaseUnit());
            ps.setDouble(3, unit.getFactorToBase());
            ps.setString(4, unit.getFamily().name());
            ps.setString(5, unit.getLabel());
            ps.setInt(6, unit.isActive() ? 1 : 0);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
            }
        }
        return -1;
    }

    public void update(CustomUnit unit) throws Exception {
        String sql = "UPDATE custom_units SET display_unit = ?, base_unit = ?, factor_to_base = ?, "
                + "family = ?, label = ?, active = ? WHERE id = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, unit.getDisplayUnit());
            ps.setString(2, unit.getBaseUnit());
            ps.setDouble(3, unit.getFactorToBase());
            ps.setString(4, unit.getFamily().name());
            ps.setString(5, unit.getLabel());
            ps.setInt(6, unit.isActive() ? 1 : 0);
            ps.setInt(7, unit.getId());
            ps.executeUpdate();
        }
    }

    public void delete(int id) throws Exception {
        String sql = "DELETE FROM custom_units WHERE id = ?";
        try (Connection conn = DatabaseManager.openConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    private CustomUnit mapRow(ResultSet rs) throws Exception {
        CustomUnit.Family family;
        try {
            family = CustomUnit.Family.valueOf(rs.getString("family"));
        } catch (Exception ex) {
            family = CustomUnit.Family.PIECE;
        }
        return new CustomUnit(
                rs.getInt("id"),
                rs.getString("display_unit"),
                rs.getString("base_unit"),
                rs.getDouble("factor_to_base"),
                family,
                rs.getString("label"),
                rs.getInt("active") == 1
        );
    }
}
