package com.glimmer.config;

import com.glimmer.common.util.CryptoUtil;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.MappedTypes;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * MyBatis TypeHandler：DB 字段级自动加解密。
 * <p>
 * - 写入 DB：明文 → 加密 → 存密文
 * - 读取 DB：密文 → 解密 → 返回明文
 * <p>
 * 业务代码完全无感知，仍调用 entity.setContent(getContent()) 读写明文。
 * 字段需用 @TableField(typeHandler = EncryptedFieldTypeHandler.class) 标注。
 */
@MappedTypes(String.class)
public class EncryptedFieldTypeHandler extends BaseTypeHandler<String> {

    // 静态持有，由 CryptoConfig 在启动时注入；TypeHandler 由 MyBatis 实例化故无法用 @Autowired
    private static CryptoUtil cryptoUtil;

    public static void setCryptoUtil(CryptoUtil util) {
        cryptoUtil = util;
    }

    private CryptoUtil crypto() {
        if (cryptoUtil == null) {
            throw new IllegalStateException("EncryptedFieldTypeHandler 尚未初始化，CryptoConfig 未注入 CryptoUtil");
        }
        return cryptoUtil;
    }

    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, String parameter, JdbcType jdbcType)
            throws SQLException {
        ps.setString(i, crypto().encrypt(parameter));
    }

    @Override
    public String getNullableResult(ResultSet rs, String columnName) throws SQLException {
        return crypto().decrypt(rs.getString(columnName));
    }

    @Override
    public String getNullableResult(ResultSet rs, int columnIndex) throws SQLException {
        return crypto().decrypt(rs.getString(columnIndex));
    }

    @Override
    public String getNullableResult(CallableStatement cs, int columnIndex) throws SQLException {
        return crypto().decrypt(cs.getString(columnIndex));
    }
}
