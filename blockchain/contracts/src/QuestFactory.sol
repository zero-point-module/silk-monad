// SPDX-License-Identifier: MIT
pragma solidity ^0.8.27;

/// @title  QuestFactory — on-chain treasure-hunt quests with native-MON rewards.
/// @notice The quest master creates a quest, escrowing a MON reward and committing to
///         keccak256(bytes(answer)). The FIRST player to submit the correct answer wins the
///         escrowed reward atomically. One persistent factory holds every quest.
/// @dev    Win-condition is an answer submission (the relic hidden in a Minecraft chest is a
///         clue, not value — only the reward is value). The first correct, unsolved claim
///         flips `solved` and pays out in one tx, so exactly one winner can ever exist; later
///         or duplicate claims revert. `claim(answer)` is plaintext in the mempool — fine for
///         a testnet AI-vs-AI demo; commit–reveal is the hardening path if needed.
contract QuestFactory {
    struct Quest {
        address creator;     // quest master who funded it
        uint256 reward;      // escrowed native MON (wei)
        bytes32 answerHash;  // keccak256(bytes(normalized answer))
        address winner;      // first correct solver (0x0 until solved)
        bool    solved;
        bool    cancelled;
    }

    Quest[] private _quests;

    event QuestCreated(uint256 indexed questId, address indexed creator, uint256 reward, bytes32 answerHash, string description);
    event QuestSolved(uint256 indexed questId, address indexed winner, uint256 reward);
    event QuestCancelled(uint256 indexed questId);

    error NoReward();
    error QuestNotFound();
    error QuestClosed();   // already solved or cancelled
    error WrongAnswer();
    error NotCreator();
    error TransferFailed();

    /// @notice Create a quest, escrowing msg.value as the reward.
    /// @param answerHash   keccak256(bytes(answer)) — commit to the secret.
    /// @param description  human-readable clue/title (indexed in the event; unused on-chain).
    /// @return questId     the new quest's id.
    function createQuest(bytes32 answerHash, string calldata description)
        external payable returns (uint256 questId)
    {
        if (msg.value == 0) revert NoReward();
        questId = _quests.length;
        _quests.push(Quest({
            creator: msg.sender,
            reward: msg.value,
            answerHash: answerHash,
            winner: address(0),
            solved: false,
            cancelled: false
        }));
        emit QuestCreated(questId, msg.sender, msg.value, answerHash, description);
    }

    /// @notice Submit an answer. The first correct + open claim wins the reward.
    function claim(uint256 questId, string calldata answer) external {
        if (questId >= _quests.length) revert QuestNotFound();
        Quest storage q = _quests[questId];
        if (q.solved || q.cancelled) revert QuestClosed();
        if (keccak256(bytes(answer)) != q.answerHash) revert WrongAnswer();

        // checks-effects-interactions: flip state BEFORE paying out (reentrancy-safe).
        q.solved = true;
        q.winner = msg.sender;
        uint256 reward = q.reward;

        emit QuestSolved(questId, msg.sender, reward);

        (bool ok, ) = payable(msg.sender).call{value: reward}("");
        if (!ok) revert TransferFailed();
    }

    /// @notice Creator reclaims the reward of an unsolved quest.
    function cancelQuest(uint256 questId) external {
        if (questId >= _quests.length) revert QuestNotFound();
        Quest storage q = _quests[questId];
        if (msg.sender != q.creator) revert NotCreator();
        if (q.solved || q.cancelled) revert QuestClosed();

        q.cancelled = true;
        uint256 reward = q.reward;

        emit QuestCancelled(questId);

        (bool ok, ) = payable(q.creator).call{value: reward}("");
        if (!ok) revert TransferFailed();
    }

    function getQuest(uint256 questId) external view returns (Quest memory) {
        if (questId >= _quests.length) revert QuestNotFound();
        return _quests[questId];
    }

    function questCount() external view returns (uint256) {
        return _quests.length;
    }
}
