package com.respondog;

import java.io.IOException;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;

@WebFilter("/")
public class ServletFilter implements Filter {

	@Override
	public void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) 
			throws IOException, ServletException {
		// this filter forwards all root context requests ("/") to "/request"
		// because the Spring Default
		request.getRequestDispatcher("/request").forward(request,response);
	}
}