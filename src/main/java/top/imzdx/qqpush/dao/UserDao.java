package top.imzdx.qqpush.dao;

import org.apache.ibatis.annotations.*;
import top.imzdx.qqpush.model.po.User;

import java.util.List;

/**
 * @author Renxing
 */
@Mapper
public interface UserDao {
    @Select("SELECT * FROM user")
    List<User> findAll();

    @Select("select * from user where name=#{name}")
    User findUserByName(String name);

    @Select("select * from user where uid=#{uid}")
    User findUserByUid(long uid);

    @Select("select * from user where cipher=#{cipher}")
    User findUserByCipher(String cipher);

    @Insert("INSERT INTO `qqmsg`.`user`(`name`, `password`, `config`, `cipher`) VALUES (#{user.name}, #{user.password}, #{user.config}, #{user.cipher})")
    int insertUser(@Param("user") User user);

    @Update("update `qqmsg`.`user` SET `name` = #{user.name}, `password` = #{user.password},`admin` = #{user.admin}, `config`= #{user.config},`cipher`=#{user.cipher} WHERE `uid` = #{user.uid}")
    int updateUser(@Param("user") User user);

}
