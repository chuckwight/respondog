package com.respondog;

import com.googlecode.objectify.ObjectifyService;

import jakarta.servlet.annotation.WebFilter;

@WebFilter(urlPatterns = {"/*"})
public class ObjectifyFilter extends ObjectifyService.Filter {}