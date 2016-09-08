package tryto.trade.web;

import java.awt.image.BufferedImage;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

import cn.skypark.code.MyCheckCodeTool;
import tryto.trade.Broker;

public class HTTrader extends WebTrader implements Broker {

	private static Logger log = LoggerFactory.getLogger(HTTrader.class);

	private boolean remove_zero;
	private String ip;
	private String mac;
	private String raw_name;
	private int use_index_start;
	private String fund_account;

	// super(HTTrader, self).__init__()
	// self.account_config = None
	// self.s = None
	// self.remove_zero = remove_zero
	//
	// self.__set_ip_and_mac()
	// self.fund_account = None
	public HTTrader(boolean remove_zero) {
		this.remove_zero = remove_zero;
		config_path = "config/ht.json";
		super.init();
		this.remove_zero = remove_zero;
		set_ip_and_mac();
	}

	/**
	 * 获取本机IP和MAC地址
	 */
	private void set_ip_and_mac() {

		try {
			// 获取ip
			InetAddress ia = InetAddress.getLocalHost();// 获取本地IP对象
			ip = ia.getHostAddress();

			// 获得网络接口对象（即网卡），并得到mac地址，mac地址存在于一个byte数组中。
			byte[] macbyte = NetworkInterface.getByInetAddress(ia).getHardwareAddress();

			// 下面代码是把mac地址拼装成String
			StringBuffer sb = new StringBuffer();

			for (int i = 0; i < macbyte.length; i++) {
				if (i != 0) {
					sb.append("-");
				}
				// mac[i] & 0xFF 是为了把byte转化为正整数
				String s = Integer.toHexString(macbyte[i] & 0xFF);
				sb.append(s.length() == 1 ? 0 + s : s);
			}

			// 把字符串所有小写字母改为大写成为正规的mac地址并返回
			mac = sb.toString().toUpperCase();
		} catch (UnknownHostException | SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private String get_user_name() {
		// 华泰账户以 08 开头的有些需移除 fund_account 开头的 0
		raw_name = account_config.get("userName").asText();
		use_index_start = 1;
		if (raw_name.startsWith("08") && remove_zero == true) {
			return raw_name.substring(1, raw_name.length());

		} else {
			return raw_name;
		}
	}

	public void read_config(String path) {
		// super(HTTrader, self).read_config(path)
		super.read_config(path);
		// self.fund_account = self.__get_user_name()
		fund_account = get_user_name();
	}

	/**
	 * 实现华泰的自动登录
	 * 
	 * @return
	 */
	public boolean login() {
		go_login_page();
		String verify_code = handle_recognize_code();
		if (verify_code == null)
			return false;
		boolean is_login = check_login_status(verify_code);
		if (!is_login) {
			return false;
		}
		JsonNode trade_info = get_trade_info();
		if (trade_info == null) {
			return false;
		}
		set_trade_need_info(trade_info);
		return true;
	}

	/**
	 * 访问登录页面获取 cookie
	 */
	private void go_login_page() {
		// if self.s is not None:
		// self.s.get(self.config['logout_api'])
		// self.s = requests.session()
		// self.s.get(self.config['login_page'])
		get(config.get("logout_api").asText());
		get(config.get("login_page").asText());
	}

	/**
	 * 获取并识别返回的验证码 // :return:失败返回 False 成功返回 验证码
	 */
	private String handle_recognize_code() {
		String verify_code = null;
		// # 获取验证码
		BufferedImage image = getBufferedImage(config.get("verify_code_api").asText());
		try {
			MyCheckCodeTool tool = new MyCheckCodeTool("huatai");
			verify_code = tool.getCheckCode_from_image(image);
			log.info("华泰验证码:{}", verify_code);
			int ht_verify_code_length = 4;
			if (verify_code.length() != ht_verify_code_length) {
				verify_code = null;
			}
		} catch (UnsupportedOperationException e) {
			e.printStackTrace();
		}
		return verify_code;
	}

	private boolean check_login_status(String verify_code) {

		JsonNode node = config.get("login");

		// 创建参数列表
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		// 设置登录所需参数
		Iterator<String> keys = node.fieldNames();
		while (keys.hasNext()) {
			String fieldName = keys.next();
			params.add(new BasicNameValuePair(fieldName, node.path(fieldName).asText()));// 使用asText，否则会带入引号“” 到String里
		}

		params.add(new BasicNameValuePair("userName", account_config.get("userName").asText()));
		params.add(new BasicNameValuePair("trdpwd", account_config.get("trdpwd").asText()));
		params.add(new BasicNameValuePair("trdpwdEns", account_config.get("trdpwd").asText()));
		params.add(new BasicNameValuePair("servicePwd", account_config.get("servicePwd").asText()));
		params.add(new BasicNameValuePair("macaddr", mac));
		params.add(new BasicNameValuePair("lipInfo", ip));
		params.add(new BasicNameValuePair("vcode", verify_code));

		log.debug("login params: {}", params);
		String login_api_response = post(config.get("login_api").asText(), params);
		if (login_api_response.indexOf("欢迎您") >= 0) {
			log.info("登录成功。");
			return true;
		} else {
			log.info("登录失败！");
		}

		return false;
	}

	/**
	 * 请求页面获取交易所需的 uid 和 password
	 */
	private JsonNode get_trade_info() {
		// trade_info_response = self.s.get(self.config['trade_info_page'])
		String trade_info_response = get(config.get("trade_info_page").asText());
		// 查找登录信息
		String JsonBase64 = "	var data = \"([/=\\w\\+]+)\""; // 得到base64的正则表达式
		Pattern Base64Pattern = Pattern.compile(JsonBase64);
		Matcher MatchBase64 = Base64Pattern.matcher(trade_info_response); // 匹配交易信息中的正则表达式
		MatchBase64.find();
		String temp = MatchBase64.toString();
		log.debug(temp);// 调试用，输出base64的交易信息
		int lenght = temp.length(); // 输出匹配后的base64的交易信息长度
		String need_data = temp.substring(95, lenght - 2); // 使用偏移量移除匹配的var data = 字段
		byte[] bytes_data = Base64.getDecoder().decode(need_data); // 将base64格式解码
		try {
			String str_data = new String(bytes_data, "gbk"); // 对解码后使用utf-8编码得到json格式交易信息
			log.info("trade info:{}", str_data);
			JsonNode rootNode  =Helpers.str2dict(str_data);
			return rootNode;
		} catch (UnsupportedEncodingException e2) {
			e2.printStackTrace();
		}
		return null;
	}

	/**
	 * 设置交易所需的一些基本参数
	 * @param json_data 登录成功返回的json数据
	 */
	private void set_trade_need_info(JsonNode json_data) {
		JsonNode account_info = json_data.get("item");
		for (int i = 0; i < account_info.size(); i++) {
			String stock_account = account_info.get(i).get("stock_account").asText();
			int main_flag = account_info.get(i).get("main_flag").asInt();
			if ((stock_account.charAt(0) == 'A' || stock_account.charAt(0) == 'B') && main_flag == 1) {// 沪 A 股东代码以 A 开头，同时需要是数字，沪 B 帐号以 C 开头，机构账户以B开头
				sh_exchange_type = account_info.get(i).get("exchange_type").asInt();
				sh_stock_account = account_info.get(i).get("stock_account").asText();
				log.debug("sh_A stock account {}", sh_stock_account);
			} else if (stock_account.charAt(0) == '0' && main_flag == 1) {// 深 A 股东代码以 0 开头，深 B 股东代码以 2 开头
				sz_exchange_type = account_info.get(i).get("exchange_type").asInt();
				sz_stock_account = account_info.get(i).get("stock_account").asText();
				log.debug("sz_A stock account {}", sz_stock_account);
			}
		}
		fund_account = json_data.get("fund_account").asText();
		client_risklevel = json_data.get("client_risklevel").asText();
		op_station = json_data.get("op_station").asText();
		trdpwd = json_data.get("trdpwd").asText();
		uid = json_data.get("uid").asText();
		branch_no = json_data.get("branch_no").asText();
	}
	
	/**
	 * 撤单
	 * 
	 * @param entrust_no
	 *            委托单号
	 */
	public void cancel_entrust(String entrust_no) {
//        cancel_params = dict(
//                self.config['cancel_entrust'],
//                entrust_no=entrust_no
//        )
//        return self.do(cancel_params)
	}

	@Override
	public StringBuilder create_basic_params() {
		StringBuilder sb = new StringBuilder();
		Random random = new Random();
		int ram = random.nextInt();
		sb.append("uid").append("=").append(uid).append("&");
		sb.append("version").append("=").append("1").append("&");
		sb.append("custid").append("=").append(account_config.get("userName").asText()).append("&");
		sb.append("op_branch_no").append("=").append(branch_no).append("&");
		sb.append("branch_no").append("=").append(branch_no).append("&");
		sb.append("op_entrust_way").append("=").append("7").append("&");
		sb.append("op_station").append("=").append(op_station).append("&");
		sb.append("fund_account").append("=").append(fund_account).append("&");
		sb.append("password").append("=").append(trdpwd).append("&");
		sb.append("identity_type").append("=").append("").append("&");
		sb.append("ram").append("=").append(ram);
		return sb;
	}
	
	@Override
	public String request(String params) {
		String b64params = Base64.getEncoder().encodeToString(params.getBytes());
		String response = get(config.get("prefix").asText() + "?" + b64params);
		byte[] responseJsonByte = Base64.getDecoder().decode(response); // 将base64格式解码
		String json = null;
			try {
				json = new String(responseJsonByte, "gbk");// 对解码后使用gbk编码得到json格式交易信息
				log.debug(json);
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} 
		return json;
	}

	@Override
	public void check_account_live(JsonNode response) {
		// TODO Auto-generated method stub
		
	}



	@Override
	public JsonNode format_response_data(String data) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public JsonNode fix_error_data(JsonNode data) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void check_login_status(JsonNode return_data) {
		// TODO Auto-generated method stub
		
	}

	// TODO: 实现买入卖出的各种委托类型
//    def buy(self, stock_code, price, amount=0, volume=0, entrust_prop=0):
//        """买入卖出股票
//        :param stock_code: 股票代码
//        :param price: 买入价格
//        :param amount: 买入股数
//        :param volume: 买入总金额 由 volume / price 取 100 的整数， 若指定 amount 则此参数无效
//        :param entrust_prop: 委托类型，暂未实现，默认为限价委托
//        """
//        params = dict(
//                self.config['buy'],
//                entrust_amount=amount if amount else volume // price // 100 * 100
//        )
//        return self.__trade(stock_code, price, entrust_prop=entrust_prop, other=params)
//
//    def sell(self, stock_code, price, amount=0, volume=0, entrust_prop=0):
//        """卖出股票
//        :param stock_code: 股票代码
//        :param price: 卖出价格
//        :param amount: 卖出股数
//        :param volume: 卖出总金额 由 volume / price 取整， 若指定 amount 则此参数无效
//        :param entrust_prop: 委托类型，暂未实现，默认为限价委托
//        """
//        params = dict(
//                self.config['sell'],
//                entrust_amount=amount if amount else volume // price
//        )
//        return self.__trade(stock_code, price, entrust_prop=entrust_prop, other=params)


//    def __trade(self, stock_code, price, entrust_prop, other):
//        need_info = self.__get_trade_need_info(stock_code)
//        return self.do(dict(
//                other,
//                stock_account=need_info['stock_account'],  # '沪深帐号'
//                exchange_type=need_info['exchange_type'],  # '沪市1 深市2'
//                entrust_prop=entrust_prop,  # 委托方式
//                stock_code='{:0>6}'.format(stock_code),  # 股票代码, 右对齐宽为6左侧填充0
//                entrust_price=price
//        ))
//
//    def __get_trade_need_info(self, stock_code):
//        """获取股票对应的证券市场和帐号"""
//        # 获取股票对应的证券市场
//        exchange_type = self.__sh_exchange_type if helpers.get_stock_type(stock_code) == 'sh' \
//            else self.__sz_exchange_type
//        # 获取股票对应的证券帐号
//        stock_account = self.__sh_stock_account if exchange_type == self.__sh_exchange_type \
//            else self.__sz_stock_account
//        return dict(
//                exchange_type=exchange_type,
//                stock_account=stock_account
//        )
//
//    def create_basic_params(self):
//        basic_params = OrderedDict(
//                uid=self.__uid,
//                version=1,
//                custid=self.account_config['userName'],
//                op_branch_no=self.__branch_no,
//                branch_no=self.__branch_no,
//                op_entrust_way=7,
//                op_station=self.__op_station,
//                fund_account=self.fund_account,
//                password=self.__trdpwd,
//                identity_type='',
//                ram=random.random()
//        )
//        return basic_params
//
//    def request(self, params):
//        headers = {
//            'User-Agent': 'Mozilla/5.0 (Windows NT 6.1; WOW64; Trident/7.0; rv:11.0) like Gecko'
//        }
//        if six.PY2:
//            item = params.pop('ram')
//            params['ram'] = item
//        else:
//            params.move_to_end('ram')
//        if six.PY2:
//            params_str = urllib.urlencode(params)
//            unquote_str = urllib.unquote(params_str)
//        else:
//            params_str = urllib.parse.urlencode(params)
//            unquote_str = urllib.parse.unquote(params_str)
//        log.debug('request params: %s' % unquote_str)
//        b64params = base64.b64encode(unquote_str.encode()).decode()
//        r = self.s.get('{prefix}/?{b64params}'.format(prefix=self.trade_prefix, b64params=b64params), headers=headers)
//        return r.content
//
//    def format_response_data(self, data):
//        bytes_str = base64.b64decode(data)
//        gbk_str = bytes_str.decode('gbk')
//        log.debug('response data before format: %s' % gbk_str)
//        filter_empty_list = gbk_str.replace('[]', 'null')
//        filter_return = filter_empty_list.replace('\n', '')
//        log.debug('response data: %s' % filter_return)
//        response_data = json.loads(filter_return)
//        if response_data['cssweb_code'] == 'error' or response_data['item'] is None:
//            return response_data
//        return_data = self.format_response_data_type(response_data['item'])
//        log.debug('response data: %s' % return_data)
//        return return_data
//
//    def fix_error_data(self, data):
//        last_no_use_info_index = -1
//        return data if hasattr(data, 'get') else data[:last_no_use_info_index]
//
//    @property
//    def exchangebill(self):
//        start_date, end_date = helpers.get_30_date()
//        return self.get_exchangebill(start_date, end_date)
//
//    def get_exchangebill(self, start_date, end_date):
//        """
//        查询指定日期内的交割单
//        :param start_date: 20160211
//        :param end_date: 20160211
//        :return:
//        """
//        params = self.config['exchangebill'].copy()
//        params.update({
//            "start_date": start_date,
//            "end_date": end_date,
//        })
//        return self.do(params)

}
