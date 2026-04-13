package com.kk.mila.mybatis.core;

import com.mybatisflex.core.BaseMapper;

public interface BaseMapper<T> extends BaseMapper<T> {
    // 通用CRUD方法由MyBatisFlex BaseMapper提供
    // 子类只需继承此接口即可获得通用方法
}