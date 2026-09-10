package com.example.voting.exception;
public class PollClosedException extends RuntimeException {
    public PollClosedException(String message) { super(message); }
}
