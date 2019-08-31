import { Component, OnInit } from '@angular/core';
import { ArticleService } from 'src/app/services/article/article.service';
import { Article } from '../../models/Article'

@Component({
  selector: 'app-home',
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent implements OnInit {
  title = 'blogify';

  articles: Article[];

  constructor(private articleService: ArticleService) { }
  ngOnInit() {
  }

  getAllArticles() {
    this.articleService.getAllArticles().subscribe((it) => {
        this.articles = it
        console.log(this.articles)
    })

  }

  createNewArticle() {
    return this.articleService.getAllArticles()
  }

}
