package com.doom.fg.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.doom.fg.entity.User;
import org.apache.ibatis.annotations.Mapper;
/**
 * Created with IntelliJ IDEA.
 *
 * @Author: doom
 * @Date: 2026/01/21/21:23
 * @Description:
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}
