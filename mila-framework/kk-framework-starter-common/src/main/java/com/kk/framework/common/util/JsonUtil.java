package com.kk.framework.common.util;

import cn.hutool.json.JSONUtil;

public class JsonUtil {
    public static String toJson(Object obj) {
        return JSONUtil.toJsonStr(obj);
    }

    public static <T> T fromJson(String json, Class<T> clazz) {
        return JSONUtil.toBean(json, clazz);
    }

    public static boolean isJson(String str) {
        return JSONUtil.isJson(str);
    }
}
