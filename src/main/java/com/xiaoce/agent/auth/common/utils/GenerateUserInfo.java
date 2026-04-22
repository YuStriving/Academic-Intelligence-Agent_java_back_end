package com.xiaoce.agent.auth.common.utils;

import java.util.UUID;

/**
 * GenerateUserInfo
 * <p>
 * TODO: 请在此处简要描述类的功能
 *
 * @author 小策
 * @date 2026/4/22 16:25
 */
public class GenerateUserInfo {
    public GenerateUserInfo(){

    }
/**
 * 生成用户昵称的方法
 * @return 返回一个以"学术用户："为前缀，加上8位UUID随机字符串组成的昵称
 */
    public static String generateUserNickName(){
    // 生成一个UUID随机字符串，并截取前8位
        String uuid = UUID.randomUUID().toString().substring(0, 8);
    // 拼接前缀并返回最终生成的用户昵称
        return "学术用户： " + uuid;
    }
}
