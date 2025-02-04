package top.imzdx.qqpush.controller;

import com.alibaba.fastjson.JSONObject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.v3.oas.annotations.Operation;
import org.hibernate.validator.constraints.Length;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import top.imzdx.qqpush.dao.UserDao;
import top.imzdx.qqpush.interceptor.LoginRequired;
import top.imzdx.qqpush.model.dto.Result;
import top.imzdx.qqpush.model.po.User;
import top.imzdx.qqpush.service.SystemService;
import top.imzdx.qqpush.service.UserService;
import top.imzdx.qqpush.utils.AuthTools;
import top.imzdx.qqpush.utils.DefinitionException;
import top.imzdx.qqpush.utils.QQConnection;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import java.io.IOException;

/**
 * @author Renxing
 */
@RestController
@RequestMapping("/user")
@Api(tags = "用户管理")
public class UserController {
    UserDao userDao;
    UserService userService;
    SystemService systemService;
    QQConnection qqConnection;
    boolean geetestOpen;
    String qqBackUrl;

    @Autowired
    public UserController(UserDao userDao, UserService userService, SystemService systemService, QQConnection qqConnection, @Value("${geetest.open}") boolean geetestOpen, @Value("${qq.back-url}") String qqBackUrl) {
        this.userDao = userDao;
        this.userService = userService;
        this.systemService = systemService;
        this.qqConnection = qqConnection;
        this.geetestOpen = geetestOpen;
        this.qqBackUrl = qqBackUrl;
    }

    @PostMapping("/login")
    @Operation(summary = "登录")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "用户名"),
            @ApiImplicitParam(name = "password", value = "密码")
    })
    public Result login(HttpServletRequest request,
                        @RequestParam @Valid @NotEmpty(message = "用户名不能为空") String name,
                        @RequestParam @Valid @NotEmpty(message = "用户名不能为空") String password) {
        User user = userService.findUserByName(name);
        if (user != null && user.getPassword().equals(password)) {
            request.getSession().setAttribute("user", user);
            return new Result<User>("登陆成功", user);
        }
        throw new DefinitionException("账号或密码错误");
    }

    @GetMapping("/qqLogin")
    @CrossOrigin
    @Operation(summary = "QQ登录回调")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "code", value = "QQ互联返回的code")
    })
    public Object qqLogin(HttpServletRequest request,
                          HttpServletResponse response,
                          @RequestParam("code") String code) {
//        第三步 获取access token
        String accessToken = qqConnection.getAccessToken(code);
//        第四步 获取登陆后返回的 openid、appid 以JSON对象形式返回
        JSONObject userInfo = qqConnection.getUserOpenID(accessToken);
//        第五步获取用户有效(昵称、头像等）信息  以JSON对象形式返回
        String oauth_consumer_key = userInfo.getString("client_id");
        String openid = userInfo.getString("openid");
        JSONObject userRealInfo = qqConnection.getUserInfo(accessToken, oauth_consumer_key, openid);

        User user = userService.findUserByOpenid(openid);
        if (user != null) {
            request.getSession().setAttribute("user", user);
            try {
                response.sendRedirect(qqBackUrl);
            } catch (IOException e) {
                e.printStackTrace();
            }
            return new Result("登陆成功", user);
        } else {
            String userName = userRealInfo.getString("nickname");
            String randomString = AuthTools.generateRandomString(6);
            do {
                userName = userName + "_" + randomString;
            } while (userService.findUserByName(userName) != null);

            if (userService.register(userName, randomString, openid)) {
                User newUser = userService.findUserByName(userName);
                request.getSession().setAttribute("user", newUser);
                try {
                    response.sendRedirect(qqBackUrl);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return new Result<User>("注册成功", newUser);
            }
        }
        throw new DefinitionException("QQ互联认证失败");
    }

    @PostMapping("/register")
    @Operation(summary = "注册", description = "当开启极验验证码时需附带geetest_challenge，geetest_validate，geetest_seccode参数")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "name", value = "用户名"),
            @ApiImplicitParam(name = "password", value = "密码")
    })
    public Result<User> register(HttpServletRequest request,
                                 @RequestParam @Valid @Length(min = 3, max = 20, message = "用户名长度需大于3,小于20") String name,
                                 @RequestParam @Valid @NotEmpty(message = "密码不能为空") String password) {
        if (geetestOpen) {
            if (!systemService.checkCaptcha(request)) {
                throw new DefinitionException("验证码错误");
            }
        }
        if (userService.findUserByName(name) != null) {
            throw new DefinitionException("该用户名已被注册，请更换后重试");
        }
        if (userService.register(name, password)) {
            User user = userService.findUserByName(name);
            request.getSession().setAttribute("user", user);
            return new Result<User>("注册成功", user);
        }
        throw new DefinitionException("注册异常");
    }

    @GetMapping("/refreshCipher")
    @LoginRequired
    @Operation(summary = "重置个人密钥")
    public Result<String> refreshCipher(HttpServletRequest request) {
        User user = (User) request.getSession().getAttribute("user");
        String cipher = userService.refreshCipher(user.getName());
        request.getSession().setAttribute("user", userService.findUserByName(user.getName()));
        return new Result<String>("密钥刷新成功", cipher);
    }

    @GetMapping("/profile")
    @LoginRequired
    @Operation(summary = "获取个人资料")
    public Result<User> getProfile(HttpServletRequest request) {
        User user = (User) request.getSession().getAttribute("user");
        if (user != null) {
            return new Result<User>("ok", user);
        }
        throw new DefinitionException("当前未登录");
    }

    @PostMapping("/qq_bot")
    @LoginRequired
    @Operation(summary = "换绑QQ机器人")
    @ApiImplicitParams({
            @ApiImplicitParam(name = "number", value = "机器人号码")
    })
    public Result<User> updateQQBot(HttpServletRequest request,
                                    @RequestParam @Valid @Length(min = 6, message = "机器人号码长度需大于6") long number) {
        User user = (User) request.getSession().getAttribute("user");
        userService.setQQBot(user.getUid(), number);
        user = userService.findUserByName(user.getName());
        request.getSession().setAttribute("user", user);
        return new Result<User>("ok", user);
    }

    @GetMapping("/ToDayUseCount")
    @LoginRequired
    @Operation(summary = "获取当日用户使用次数")
    public Result<Integer> selectToDayUserUseCount(HttpServletRequest request) {
        User user = (User) request.getSession().getAttribute("user");
        if (user != null) {
            return new Result<Integer>("ok", userService.selectToDayUserUseCount(user.getUid()));
        }
        throw new DefinitionException("当前未登录");
    }

}
