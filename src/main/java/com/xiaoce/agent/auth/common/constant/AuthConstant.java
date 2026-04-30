package com.xiaoce.agent.auth.common.constant;

import java.security.SecureRandom;
import java.time.format.DateTimeFormatter;
import java.util.regex.Pattern;

/**
 * AuthFormConstant
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/24 19:59
 */
public class AuthConstant {
    public AuthConstant(){

    }
    public static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    public static final DateTimeFormatter DAY_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");




}
