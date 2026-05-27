package com.nc.stock.project.stock;

import java.io.IOException;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.nc.stock.project.test.JsonFileService;
import com.nc.stock.project.test.UserDto;

import lombok.extern.slf4j.Slf4j;


@Slf4j
@RestController
@RequestMapping({"","/", "//"})
public class MainController {

    private final JsonFileService jsonFileService;

    public MainController(JsonFileService jsonFileService) {
        this.jsonFileService = jsonFileService;
    }

    //@PostMapping("/{fileName}")
    //public ResponseEntity<String> createFile(@PathVariable("fileName") String fileName, @RequestBody UserDto dto) {
    @GetMapping({"","/", "/index"})
    public ResponseEntity<String> index(@ModelAttribute  UserDto dto) {
        try {
            //jsonFileService.createJsonFile(fileName, dto);
            return ResponseEntity.ok("새로운 것 준비중.");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("파일 생성 실패: " + e.getMessage());
            //return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("토큰이 만료되었거나 올바르지 않습니다.");
        }
    }

}
