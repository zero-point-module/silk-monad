// SPDX-License-Identifier: MIT
pragma solidity ^0.8.27;

import {ERC20} from "@openzeppelin/contracts/token/ERC20/ERC20.sol";

/// @title  Good — a tradeable Silk Road commodity.
/// @notice Plain OpenZeppelin ERC-20, deployed once per good (SPICE, SILK, JADE).
///         The entire initial supply is minted to the deployer, which then
///         distributes starting balances to the agent wallets.
contract Good is ERC20 {
    /// @param name_              human-readable name, e.g. "Spice"
    /// @param symbol_            ticker, e.g. "SPICE"
    /// @param initialSupplyWhole supply in WHOLE tokens (scaled by 10**decimals here)
    constructor(string memory name_, string memory symbol_, uint256 initialSupplyWhole)
        ERC20(name_, symbol_)
    {
        _mint(msg.sender, initialSupplyWhole * 10 ** decimals());
    }
}
