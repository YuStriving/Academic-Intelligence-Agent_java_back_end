package com.xiaoce.agent.auth.common.utils;

import com.xiaoce.agent.auth.common.exception.BusinessException;
import com.xiaoce.agent.auth.common.exception.ErrorCode;
import org.springframework.util.StringUtils;

import java.util.Locale;

import static com.xiaoce.agent.auth.common.constant.AuthConstant.EMAIL_PATTERN;

/**
 * UserInfoUtils
 * <p>
 * 用户信息工具类，提供用户名、邮箱、学术ID等用户信息的格式化和验证方法。
 *
 * @author 小策
 * @date 2026/4/25 20:53
 */
public class UserInfoUtils {

    private UserInfoUtils() {
    }

    /**
     * 规范化用户名格式
     *
     * <p>去除用户名首尾的空格，确保用户名格式的一致性。
     *
     * @param username 原始用户名
     * @return 规范化后的用户名
     */
    public static String normalizeUsername(String username) {
        return username == null ? null : username.trim();
    }

    /**
     * 规范化邮箱格式
     *
     * <p>将邮箱转换为小写并去除首尾空格，确保邮箱格式的一致性。
     * 这样可以避免因为大小写或空格导致的问题。
     *
     * @param email 原始邮箱地址
     * @return 规范化后的邮箱地址
     */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 规范化学术ID格式
     *
     * <p>去除学术ID首尾的空格，确保学术ID格式的一致性。
     *
     * @param academicId 原始学术ID
     * @return 规范化后的学术ID
     */
    public static String normalizeAcademicId(String academicId) {
        return academicId == null ? null : academicId.trim();
    }

    /**
     * 验证邮箱格式
     *
     * <p>检查邮箱是否为空、是否符合标准格式、长度是否合理。
     * 邮箱长度不能超过254个字符（RFC 5321标准）。
     *
     * @param email 需要验证的邮箱地址
     */
    public static void validateEmail(String email) {
        if (!StringUtils.hasText(email)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Email is required");
        }
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Invalid email format");
        }
        if (email.length() > 254) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Email is too long");
        }
    }

    /**
     * 验证用户名格式
     *
     * <p>检查用户名是否为空、长度是否在3-20之间、是否只包含字母数字下划线、是否以字母开头。
     * 这些限制是为了保证用户名的规范性和安全性。
     *
     * @param username 需要验证的用户名
     */
    public static void validateUsername(String username) {
        if (!StringUtils.hasText(username)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Username is required");
        }
        if (username.length() < 3 || username.length() > 20) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Username length must be 3-20");
        }
        if (!username.matches("^[a-zA-Z0-9_]+$")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Username can only contain letters, digits and underscore");
        }
        if (Character.isDigit(username.charAt(0))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "Username cannot start with digit");
        }
    }

    /**
     * 验证学术ID格式
     *
     * <p>检查学术ID是否为空、长度是否为8位、是否只包含数字。
     * 学术ID格式：前2位表示年份（如24表示2024年），后6位为唯一标识。
     * 学术ID格式示例：24613341。
     *
     * @param academicId 需要验证的学术ID
     */
    public static void validateAcademicId(String academicId) {
        if (!StringUtils.hasText(academicId)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "必须要有学术id");
        }
        if (academicId.length() != 8) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "学术id必须为8个字符");
        }
        if (!academicId.matches("^[A-Za-z0-9]{8}$")) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "不符和学术id格式");
        }
    }
}
