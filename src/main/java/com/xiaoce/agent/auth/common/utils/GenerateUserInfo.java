package com.xiaoce.agent.auth.common.utils;

import java.time.Year;
import java.util.UUID;

public final class GenerateUserInfo {

    private GenerateUserInfo() {
    }

    public static String generateUserNickName() {
        String uuid = UUID.randomUUID().toString().substring(0, 8);
        return "学术用户:" + uuid;
    }

    public static String getAvatarUrl() {
        return "https://xiaoce-zhiguang.oss-cn-shenzhen.aliyuncs.com/default.jpg";
    }

    public static String getBio() {
        return "这个人很懒，什么都没有留下";
    }

    public static String getSchool() {
        return "未认证用户";
    }

    public static String getAcademicId() {
        int year = Year.now().getValue() % 100;
        String yearPart = String.format("%02d", year);
        long timestampPart = System.currentTimeMillis() % 1_000_000;
        int randomPart = (int) (Math.random() * 1000);
        String uniquePart = String.format("%06d", (timestampPart + randomPart) % 1_000_000);
        return yearPart + uniquePart;
    }

    public static String getDefaultPassword() {
        return "123456";
    }
}
