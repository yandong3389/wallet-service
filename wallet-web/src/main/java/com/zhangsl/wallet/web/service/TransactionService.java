package com.zhangsl.wallet.web.service;

import com.alibaba.fastjson.JSONObject;
import com.zhangsl.wallet.blockcoin.btc.BitCoinService;
import com.zhangsl.wallet.blockcoin.common.BTCFactory;
import com.zhangsl.wallet.blockcoin.common.EthWeb3jClient;
import com.zhangsl.wallet.blockcoin.common.JsonRpcHandler;
import com.zhangsl.wallet.common.ErrorCode;
import com.zhangsl.wallet.common.coin.CoinType;
import com.zhangsl.wallet.common.eth.EthConstant;
import com.zhangsl.wallet.common.eth.EthHandle;
import com.zhangsl.wallet.common.exception.WalletException;
import com.zhangsl.wallet.web.bean.*;
import com.zhangsl.wallet.web.core.Factory;
import com.zhangsl.wallet.web.core.Wallet;
import com.zhangsl.wallet.web.core.impl.ContractWallet;
import com.zhangsl.wallet.web.redis.RedisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.web3j.protocol.Web3j;

import java.math.BigDecimal;
import java.math.BigInteger;

@Service
public class TransactionService {

    private static final Logger logger = LoggerFactory.getLogger(TransactionService.class);

    @Autowired
    private Factory factory;

    @Autowired
    private RedisService redisService;

    @Autowired
    private ContractWallet contractWallet;

    public TransactionMessage query(String coinType, String transactionId) {
        Wallet wallet = getWallet(coinType);
        TransactionMessage transaction = wallet.getTransaction(coinType, transactionId);
        return transaction;
    }

    /**
     * 创建一个transcation
     *
     * @param transcation
     * @return
     */
    public String create(Transcation transcation) {
        String coinType = transcation.getCoinType().toUpperCase();
        boolean isExist = redisService.hasAddress(coinType, transcation.getFrom());
        if (!isExist) {
            throw new WalletException(ErrorCode.ADDRESS_ERROR);
        }

        Wallet wallet = getWallet(coinType);
        //提现的金额，每个币都不一样
        //需要的费用信息
        BigDecimal gasPrice = null;
        if (transcation.getGas() != null) {
            gasPrice = transcation.getGas();
        }
        String transactionString = wallet.createTransaction(coinType, transcation.getFrom(), transcation.getTo(), transcation.getAmount(), gasPrice);
        return transactionString;
    }


    /**
     * 创建一个合约信息
     *
     * @param contract
     * @return
     */
    public String createContract(Contract contract) {
        Web3j web3j = EthWeb3jClient.getInstance().getClient();
        //获取gasprice信息
        BigInteger gasInteger = null;
        if (contract.getGasPrice() != null) {
            gasInteger = EthHandle.etherToWei(contract.getGasPrice());
        } else {
            //默认用以太坊网络的gas
            gasInteger = EthHandle.getInstance().getGasPrice(web3j);
        }
        //gaslimit 信息
        if (contract.getGasLimit() == null) {
            contract.setGasLimit(EthConstant.TOKEN_GAS_LIMIT);
        }
        return contractWallet.createContract(contract.getFrom(), gasInteger, contract.getGasLimit(), contract.getData());
    }

    /**
     * 发送加密后的信息
     *
     * @param sendTranscation
     * @return
     */
    public String send(SendTranscation sendTranscation) {
        String coinType = sendTranscation.getCoinType();
        Wallet wallet = getWallet(coinType);
        String transactionId = wallet.sendSignedTransaction(coinType, sendTranscation.getTransaction());
        redisService.setTxid(coinType,transactionId);
        redisService.setTxSign(transactionId, sendTranscation.getTransaction());
        return transactionId;
    }


    public JSONObject decodeHash(String coinType,String hash) {
        BitCoinService bitCoinService = BTCFactory.getBitCoinService(coinType.toUpperCase());
        JSONObject jsonObject = bitCoinService.decodeRawTransaction(hash);
        JsonRpcHandler.checkException(jsonObject);
        return jsonObject.getJSONObject("result");
    }


    public String register(String body) {
        Web3j web3j = EthWeb3jClient.getInstance().getClient();
        //获取gasprice信息
        BigInteger gasInteger = EthHandle.getInstance().getGasPrice(web3j);

        //直接给一个大点的gas,最好大于100000
        BigInteger gasLimit = BigInteger.valueOf(108888L);
        JSONObject jsonObject = JSONObject.parseObject(body);
        String from = jsonObject.getString("from");
        String key = jsonObject.getString("key");
        if (StringUtils.isEmpty(from) || StringUtils.isEmpty(key)) {
            throw new WalletException(ErrorCode.PARAM_ERROR);
        }
        return contractWallet.registerContract(from, key, gasInteger, gasLimit);
    }

    /**
     * 查询区块的余额信息
     *
     * @param queryBalance
     * @return
     */
    public BigDecimal queryBalance(QueryBalance queryBalance) {
        String coinType = queryBalance.getCoinType().toUpperCase();
        Wallet wallet = getWallet(coinType);
        BigDecimal balance = wallet.queryBalance(coinType, queryBalance.getAddress());
        return balance;
    }

    private Wallet getWallet(String coinType) {
        String walletName = CoinType.getCoinWallet(coinType);
        if (StringUtils.isEmpty(walletName)) {
            throw new WalletException("no suport wallet");
        }
        Wallet wallet = factory.getWallet(walletName);
        if (wallet == null) {
            throw new WalletException("no suport wallet");
        }
        return wallet;
    }
}
