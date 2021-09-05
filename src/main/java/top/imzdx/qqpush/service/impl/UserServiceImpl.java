package top.imzdx.qqpush.service.impl;

import com.alibaba.fastjson.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import top.imzdx.qqpush.dao.QqInfoDao;
import top.imzdx.qqpush.dao.UserDao;
import top.imzdx.qqpush.model.po.User;
import top.imzdx.qqpush.service.UserService;
import top.imzdx.qqpush.utils.AuthTools;


/**
 * @author Renxing
 */
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    QqInfoDao qqInfoDao;
    @Autowired
    UserDao userDao;

    @Override
    public boolean register(String name, String password) {
        User user = new User()
                .setName(name)
                .setPassword(password)
                .setCipher(AuthTools.generateCipher())
                .setConfig(new JSONObject() {{
                    put("qq_bot", qqInfoDao.getFirst().getNumber());
                }}.toJSONString());
        int i = userDao.InsertUser(user);
        if (i == 1) {
            return true;
        }
        return false;
    }

    @Override
    public User findUserByName(String name) {
        return userDao.findUserByName(name);
    }
}
