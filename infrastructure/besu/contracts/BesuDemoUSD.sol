// SPDX-License-Identifier: MIT
pragma solidity 0.8.30;

import {ERC20} from "@openzeppelin/contracts/token/ERC20/ERC20.sol";

/// Development-only fixed supply. Not Circle USDC, backed USD or redeemable fiat.
/// No owner, later mint, proxy, transfer tax or public-network deployment path.
contract BesuDemoUSD is ERC20 {
    constructor(address treasury) ERC20("Besu Demo USD", "DEMOUSD") {
        require(treasury != address(0), "TREASURY_REQUIRED");
        _mint(treasury, 1_000_000 * 10 ** decimals());
    }

    function decimals() public pure override returns (uint8) { return 6; }
}
